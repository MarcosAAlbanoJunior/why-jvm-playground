package io.whyjvm.playground.bootstrap;

import io.whyjvm.playground.catalog.Author;
import io.whyjvm.playground.catalog.AuthorRepository;
import io.whyjvm.playground.catalog.Book;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carga inicial do catalogo. Cada livro tem o <b>seu proprio autor</b> (1:1), de
 * proposito: assim o N+1 escala com o tamanho da pagina. Percorrer uma pagina de
 * {@code limit} livros e tocar no autor lazy de cada um dispara <b>uma query por
 * livro</b> — {@code limit=300} faz ~300 queries e a rota fica nitidamente lenta.
 *
 * <p>Se os autores fossem reusados (poucos autores, muitos livros), o persistence
 * context cacheria cada autor apos a 1a query e o N+1 saturaria em "numero de
 * autores distintos" — invisivel no tempo total. Por isso 1 autor por livro.
 *
 * <p>E idempotente: so popula quando o banco esta vazio, entao reiniciar a
 * aplicacao (ou o container) nao duplica os dados.
 *
 * <p>Roda antes do {@link LatencyBaselineWarmer} (ver {@code @Order}) porque o
 * aquecimento precisa de livros ja gravados para escolher rotas validas.
 */
@Component
@Order(0)
public class DataSeeder implements CommandLineRunner {

    /** Tamanho do catalogo. >= MAX_LIMIT do controller, para o N+1 escalar ate o teto. */
    private static final int CATALOG_SIZE = 500;

    /** Nomes-base reaproveitados (com sufixo numerico) so para variar a massa. */
    private static final List<String> BASE_NAMES = List.of(
            "Machado de Assis", "Clarice Lispector", "Jorge Amado", "Graciliano Ramos",
            "Guimaraes Rosa", "Cecilia Meireles", "Carlos Drummond", "Mario de Andrade",
            "Lima Barreto", "Rachel de Queiroz", "Erico Verissimo", "Monteiro Lobato");

    private final AuthorRepository authors;

    public DataSeeder(AuthorRepository authors) {
        this.authors = authors;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (authors.count() > 0) {
            return;
        }
        List<Author> catalog = new ArrayList<>(CATALOG_SIZE);
        for (int i = 1; i <= CATALOG_SIZE; i++) {
            Author author = new Author(authorName(i), "Brasil");
            BigDecimal price = BigDecimal.valueOf(2900L + (i * 137L % 7000L), 2);
            author.addBook(new Book("Volume %04d".formatted(i), price));
            catalog.add(author);
        }
        authors.saveAll(catalog);
    }

    /** Um nome distinto por autor: nome-base ciclico + indice, garantindo unicidade. */
    private String authorName(int i) {
        return "%s %03d".formatted(BASE_NAMES.get(i % BASE_NAMES.size()), i);
    }
}
