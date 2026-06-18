package io.whyjvm.playground.catalog;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

/**
 * Autor de uma obra. Lado "um" da relacao com {@link Book}.
 *
 * <p>A colecao de livros e <b>lazy</b> de proposito: e o que torna o cenario de
 * N+1 realista. Quando o codigo percorre uma lista de livros e, para cada um,
 * acessa {@code book.getAuthor().getName()}, o Hibernate dispara uma query
 * separada por autor ainda nao carregado.
 */
@Entity
@Table(name = "author")
public class Author {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String country;

    @OneToMany(mappedBy = "author", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Book> books = new ArrayList<>();

    protected Author() {
    }

    public Author(String name, String country) {
        this.name = name;
        this.country = country;
    }

    /** Vincula o livro nos dois lados da relacao, mantendo o grafo consistente. */
    public void addBook(Book book) {
        books.add(book);
        book.assignTo(this);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCountry() {
        return country;
    }

    public List<Book> getBooks() {
        return books;
    }
}
