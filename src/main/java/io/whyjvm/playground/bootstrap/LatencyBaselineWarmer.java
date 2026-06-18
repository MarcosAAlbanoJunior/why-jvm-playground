package io.whyjvm.playground.bootstrap;

import io.whyjvm.playground.catalog.CatalogService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Aquece o baseline de latencia do why-jvm logo apos a subida.
 *
 * <p>O gatilho de lentidao do agente e <b>por rota</b>: so acusa SLOW quando uma
 * requisicao passa de um multiplo do p99 daquela rota, e so depois de ~50 amostras.
 * Sem trafego inicial, a primeira chamada lenta nao teria baseline para vencer.
 *
 * <p>Por isso a aplicacao faz, nela mesma, varias chamadas HTTP nas rotas rapidas
 * (detalhe de livro e listagem pequena). As chamadas passam pelo agente como
 * qualquer request, formando um p99 baixo — dai um {@code slowDownstreamMs} alto
 * ou um {@code limit} grande se destacam e disparam o incidente.
 */
@Component
public class LatencyBaselineWarmer {

    private static final Logger log = LoggerFactory.getLogger(LatencyBaselineWarmer.class);

    private final CatalogService catalog;
    private final boolean enabled;
    private final int iterations;
    private final int port;

    public LatencyBaselineWarmer(CatalogService catalog,
                                 @Value("${playground.warmup.enabled:true}") boolean enabled,
                                 @Value("${playground.warmup.iterations:120}") int iterations,
                                 @Value("${server.port:8080}") int port) {
        this.catalog = catalog;
        this.enabled = enabled;
        this.iterations = iterations;
        this.port = port;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        if (!enabled) {
            log.info("Aquecimento de baseline desabilitado (playground.warmup.enabled=false)");
            return;
        }
        Thread worker = new Thread(this::hammerFastRoutes, "baseline-warmer");
        worker.setDaemon(true);
        worker.start();
    }

    /** Bate repetidamente nas rotas rapidas, em segundo plano, para formar o p99. */
    private void hammerFastRoutes() {
        List<Long> bookIds = catalog.sampleBookIds(5);
        if (bookIds.isEmpty()) {
            log.warn("Sem livros para aquecer o baseline; pulando aquecimento");
            return;
        }

        RestClient client = RestClient.create("http://localhost:" + port);
        log.info("Aquecendo baseline: {} iteracoes nas rotas rapidas", iterations);
        for (int i = 0; i < iterations; i++) {
            Long bookId = bookIds.get(i % bookIds.size());
            warmRoute(client, "/api/catalog/books/" + bookId);
            warmRoute(client, "/api/catalog/books?limit=5");
        }
        log.info("Baseline aquecido: rotas rapidas prontas para destacar lentidao");
    }

    private void warmRoute(RestClient client, String path) {
        try {
            client.get().uri(path).retrieve().toBodilessEntity();
        } catch (RuntimeException ignored) {
            // Falhas pontuais de aquecimento nao importam: a meta e so gerar trafego.
        }
    }
}
