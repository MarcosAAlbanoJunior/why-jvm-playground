package io.whyjvm.playground.catalog;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catalogo de livros. Reune dois cenarios do why-jvm:
 *
 * <ul>
 *   <li><b>N+1 (Tier 3)</b>: {@code GET /api/catalog/books} percorre uma pagina
 *       e toca no autor lazy de cada livro. Com {@code limit} grande, o enxame de
 *       queries deixa a rota lenta e o incidente expoe o padrao na arvore do trace.
 *   <li><b>Lentidao (Tier 1)</b>: {@code GET /api/catalog/books/{id}} e uma busca
 *       rapida por id; o parametro {@code slowDownstreamMs} simula uma dependencia
 *       externa lenta na <b>mesma rota</b> ja aquecida, disparando o alerta de SLOW.
 * </ul>
 */
@RestController
@RequestMapping("/api/catalog")
@Tag(name = "Catalogo (N+1 e lentidao)", description = "Cenarios Tier 3 (N+1) e Tier 1 (lentidao)")
public class CatalogController {

    /** Teto de itens por pagina: evita pedidos absurdos, mas deixa o N+1 visivel. */
    private static final int MAX_LIMIT = 500;

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    /**
     * Listagem <b>naive</b> (N+1). Comece com {@code limit} pequeno (aquecimento) e
     * depois suba para algo como 300 para provocar o incidente.
     */
    @GetMapping("/books")
    @Operation(summary = "Listagem naive (N+1)",
            description = "1 query da pagina + 1 por livro (autor lazy). Suba o limit para ~300 e dispare o incidente.")
    public BookPage listBooks(@RequestParam(defaultValue = "5") int limit) {
        return catalog.listBooksNaive(clampLimit(limit));
    }

    /** Mesma listagem, porem com {@code join fetch}: a versao sem N+1, para comparar. */
    @GetMapping("/books/optimized")
    @Operation(summary = "Listagem otimizada (join fetch)",
            description = "Mesma resposta, uma unica query. Versao sem N+1, para comparar.")
    public BookPage listBooksOptimized(@RequestParam(defaultValue = "5") int limit) {
        return catalog.listBooksOptimized(clampLimit(limit));
    }

    /**
     * Detalhe de um livro por id (rota rapida e aquecida). Informe
     * {@code slowDownstreamMs} para simular uma chamada externa lenta e disparar
     * o alerta de lentidao sem mudar a rota.
     */
    @GetMapping("/books/{id}")
    @Operation(summary = "Detalhe de livro (lentidao)",
            description = "Busca rapida por id (rota aquecida). slowDownstreamMs simula dependencia lenta e dispara SLOW.")
    public BookView getBook(@PathVariable Long id,
                            @RequestParam(defaultValue = "0") long slowDownstreamMs) {
        BookView book = catalog.findBook(id);
        simulateSlowDownstream(slowDownstreamMs);
        return book;
    }

    private int clampLimit(int limit) {
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }

    /**
     * Bloqueia a thread do request por {@code millis} para imitar uma dependencia
     * externa lenta (gateway de pagamento, API parceira, etc.). A duracao entra no
     * span do request, que e o que o gatilho de lentidao do why-jvm observa.
     */
    private void simulateSlowDownstream(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
