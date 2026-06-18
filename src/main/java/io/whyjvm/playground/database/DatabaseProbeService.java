package io.whyjvm.playground.database;

import com.zaxxer.hikari.HikariDataSource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Exercicios contra o banco real (PostgreSQL) e contra o pool de conexoes.
 *
 * <p>Como o agente OpenTelemetry instrumenta JDBC, cada query vira um sub-span:
 * tanto a latencia do banco quanto a contencao de pool aparecem na arvore do
 * trace capturada pelo why-jvm.
 */
@Service
public class DatabaseProbeService {

    private final JdbcTemplate jdbc;
    private final HikariDataSource dataSource;

    public DatabaseProbeService(JdbcTemplate jdbc, HikariDataSource dataSource) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
    }

    /**
     * Segura uma conexao por {@code seconds} no proprio banco via {@code pg_sleep}.
     * Latencia real do lado do servidor, nao {@code Thread.sleep} na aplicacao.
     */
    public void runSlowQuery(double seconds) {
        jdbc.query(connection -> {
            var statement = connection.prepareStatement("SELECT pg_sleep(?)");
            statement.setDouble(1, seconds);
            return statement;
        }, resultSet -> null);
    }

    /**
     * Exaure o pool DE VERDADE. Ocupa <b>todas</b> as conexoes do pool com
     * {@code pg_sleep} em background e, na propria thread do request, pede mais uma.
     * Sem conexao livre, o HikariCP estoura com o erro <b>nativo</b>
     * ({@code SQLTransientConnectionException}: "Connection is not available, request
     * timed out after Nms") — exatamente o que um app de producao veria, sem nenhuma
     * excecao de dominio mapeada por cima. O erro sobe, o request termina em 500 e o
     * why-jvm dispara o incidente.
     *
     * <p>O tempo de posse e ancorado no {@code connection-timeout} do proprio pool:
     * as conexoes ficam seguras alem do tempo que o request espera, garantindo que o
     * estouro seja por exaustao (e nao uma corrida que as vezes passa).
     */
    public void exhaustPool() {
        int poolSize = dataSource.getMaximumPoolSize();
        // Segura as conexoes por mais tempo do que o request espera por uma livre,
        // senao elas seriam liberadas antes de o pool de fato esgotar.
        double holdSeconds = dataSource.getConnectionTimeout() / 1000.0 + 2.0;

        ExecutorService holders = Executors.newFixedThreadPool(poolSize);
        CountDownLatch allAcquired = new CountDownLatch(poolSize);
        try {
            for (int i = 0; i < poolSize; i++) {
                holders.submit(() -> holdOneConnection(allAcquired, holdSeconds));
            }
            // So pressiona o pool depois que todas as conexoes ja foram tomadas.
            if (!allAcquired.await((long) Math.ceil(holdSeconds) + 5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Nao foi possivel saturar o pool a tempo");
            }
            // Pool esgotado: esta chamada espera o connection-timeout e entao o
            // HikariCP lanca o erro nativo de indisponibilidade de conexao.
            jdbc.queryForObject("SELECT 1", Integer.class);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Cenario de pool interrompido", interrupted);
        } finally {
            // shutdown() (e nao shutdownNow): deixa os holders liberarem as conexoes
            // ao fim do pg_sleep, sem interromper queries no meio.
            holders.shutdown();
        }
    }

    /** Pega uma conexao do pool e a segura por {@code holdSeconds}, sinalizando ao toma-la. */
    private Void holdOneConnection(CountDownLatch acquired, double holdSeconds) {
        jdbc.query(connection -> {
            acquired.countDown(); // conexao em maos: o pool perdeu mais uma
            var statement = connection.prepareStatement("SELECT pg_sleep(?)");
            statement.setDouble(1, holdSeconds);
            return statement;
        }, resultSet -> null);
        return null;
    }
}
