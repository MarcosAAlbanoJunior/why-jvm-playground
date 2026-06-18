package io.whyjvm.playground.database;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cenarios de <b>banco de dados</b> contra um PostgreSQL real.
 *
 * <ul>
 *   <li>{@code GET /api/database/slow-query}: uma query genuinamente lenta
 *       ({@code pg_sleep}) que segura uma conexao — aparece como sub-span no trace.
 *   <li>{@code POST /api/database/saturate-pool}: ocupa todas as conexoes do pool
 *       e pede mais uma, ate o HikariCP estourar com o erro nativo de conexao
 *       indisponivel. O erro sobe sem tratamento, o request termina em erro e
 *       dispara o incidente.
 * </ul>
 */
@RestController
@RequestMapping("/api/database")
@Tag(name = "Banco (conexao)", description = "Query lenta e exaustao do pool de conexoes")
public class DatabaseController {

    /** Teto de seguranca para nao deixar uma chamada segurar o banco indefinidamente. */
    private static final double MAX_SECONDS = 30.0;

    private final DatabaseProbeService probe;

    public DatabaseController(DatabaseProbeService probe) {
        this.probe = probe;
    }

    /** Uma unica query lenta no banco, segurando a conexao por {@code seconds}. */
    @GetMapping("/slow-query")
    @Operation(summary = "Query lenta", description = "Uma query que segura uma conexao por N segundos (pg_sleep).")
    public PoolPressureReport slowQuery(@RequestParam(defaultValue = "3") double seconds) {
        probe.runSlowQuery(clampSeconds(seconds));
        return new PoolPressureReport(1, 1, 0, null);
    }

    /**
     * Esgota o pool de conexoes ate o limite real: ocupa todas as conexoes e pede
     * mais uma. O HikariCP lanca o erro <b>nativo</b> de conexao indisponivel, que
     * sobe sem tratamento — o request termina em 500 e dispara o incidente. Nao
     * recebe parametros: o tamanho do pool e o timeout vem da config do proprio pool.
     */
    @PostMapping("/saturate-pool")
    @Operation(summary = "Esgotar pool", description = "Ocupa todas as conexoes e pede mais uma: erro nativo do HikariCP (Connection is not available).")
    public void saturatePool() {
        probe.exhaustPool();
    }

    private double clampSeconds(double seconds) {
        return Math.max(0.0, Math.min(seconds, MAX_SECONDS));
    }
}
