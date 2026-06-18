package io.whyjvm.playground.catalog;

import java.math.BigDecimal;

/** Projecao de leitura de um livro, ja com os dados do autor resolvidos. */
public record BookView(Long id, String title, BigDecimal price, String authorName, String authorCountry) {

    static BookView from(Book book) {
        Author author = book.getAuthor();
        return new BookView(book.getId(), book.getTitle(), book.getPrice(), author.getName(), author.getCountry());
    }
}
