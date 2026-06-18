package io.whyjvm.playground.bootstrap;

import io.whyjvm.playground.catalog.Author;
import io.whyjvm.playground.catalog.AuthorRepository;
import io.whyjvm.playground.catalog.Book;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carga inicial do catalogo. Cria autores e um bom volume de livros para que o
 * cenario de N+1 tenha massa de dados suficiente — uma pagina grande de livros
 * gera dezenas de queries por causa do autor lazy.
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

    private static final int BOOKS_PER_AUTHOR = 35;

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
        seedAuthors().forEach(this::fillCatalog);
    }

    private List<Author> seedAuthors() {
        return List.of(
                new Author("Machado de Assis", "Brasil"),
                new Author("Clarice Lispector", "Brasil"),
                new Author("Jorge Amado", "Brasil"),
                new Author("Graciliano Ramos", "Brasil"),
                new Author("Guimaraes Rosa", "Brasil"),
                new Author("Cecilia Meireles", "Brasil"),
                new Author("Carlos Drummond", "Brasil"),
                new Author("Mario de Andrade", "Brasil"),
                new Author("Lima Barreto", "Brasil"),
                new Author("Rachel de Queiroz", "Brasil"),
                new Author("Erico Verissimo", "Brasil"),
                new Author("Monteiro Lobato", "Brasil"));
    }

    /** Gera {@value #BOOKS_PER_AUTHOR} livros para o autor e persiste em cascata. */
    private void fillCatalog(Author author) {
        for (int volume = 1; volume <= BOOKS_PER_AUTHOR; volume++) {
            BigDecimal price = BigDecimal.valueOf(2900L + (volume * 137L % 7000L), 2);
            author.addBook(new Book("%s - Volume %02d".formatted(author.getName(), volume), price));
        }
        authors.save(author);
    }
}
