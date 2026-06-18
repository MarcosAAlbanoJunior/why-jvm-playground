package io.whyjvm.playground.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * Livro do catalogo. Lado "muitos" da relacao com {@link Author}.
 *
 * <p>O autor e carregado de forma <b>lazy</b>: percorrer uma pagina de livros e
 * tocar no autor de cada um, sem um fetch join, e exatamente o gatilho classico
 * do problema de N+1 que este projeto serve para demonstrar.
 */
@Entity
@Table(name = "book")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private BigDecimal price;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private Author author;

    protected Book() {
    }

    public Book(String title, BigDecimal price) {
        this.title = title;
        this.price = price;
    }

    /** Usado por {@link Author#addBook} para fechar o vinculo bidirecional. */
    void assignTo(Author author) {
        this.author = author;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Author getAuthor() {
        return author;
    }
}
