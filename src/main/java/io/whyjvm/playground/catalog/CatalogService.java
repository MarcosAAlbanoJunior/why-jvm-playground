package io.whyjvm.playground.catalog;

import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leitura do catalogo de livros.
 *
 * <p>Os metodos sao {@code @Transactional(readOnly = true)} porque o carregamento
 * lazy do autor precisa de uma sessao aberta. Como {@code open-in-view} esta
 * desligado, e aqui — e so aqui — que o N+1 do cenario naive acontece.
 */
@Service
public class CatalogService {

    private final BookRepository books;

    public CatalogService(BookRepository books) {
        this.books = books;
    }

    /**
     * Versao <b>naive</b>: le a pagina de livros (1 query) e, ao montar a
     * projecao, toca no autor de cada livro. Como o autor e lazy e nao houve
     * fetch join, cada autor ainda nao carregado vira uma query — o N+1.
     */
    @Transactional(readOnly = true)
    public BookPage listBooksNaive(int limit) {
        List<Book> page = books.findPage(PageRequest.of(0, limit));
        List<BookView> views = page.stream().map(BookView::from).toList();
        return new BookPage("naive", limit, views.size(), views);
    }

    /**
     * Versao <b>otimizada</b>: um unico {@code join fetch} traz livros e autores
     * juntos. Mesma resposta da versao naive, mas sem o enxame de queries.
     */
    @Transactional(readOnly = true)
    public BookPage listBooksOptimized(int limit) {
        List<Book> page = books.findPageWithAuthor(PageRequest.of(0, limit));
        List<BookView> views = page.stream().map(BookView::from).toList();
        return new BookPage("optimized", limit, views.size(), views);
    }

    /** Busca rapida por chave primaria. E a rota "normal" usada no aquecimento. */
    @Transactional(readOnly = true)
    public BookView findBook(Long id) {
        return books.findById(id).map(BookView::from).orElseThrow(() -> new BookNotFoundException(id));
    }

    /** Ids reais para o aquecedor de baseline escolher rotas validas. */
    @Transactional(readOnly = true)
    public List<Long> sampleBookIds(int howMany) {
        return books.findPage(PageRequest.of(0, howMany)).stream().map(Book::getId).toList();
    }
}
