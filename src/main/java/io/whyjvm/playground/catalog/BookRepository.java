package io.whyjvm.playground.catalog;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Acesso a {@link Book}, com as duas estrategias de leitura do cenario de N+1. */
public interface BookRepository extends JpaRepository<Book, Long> {

    /**
     * Le uma pagina de livros <b>sem</b> trazer o autor. Tocar no autor de cada
     * livro depois (fora de um fetch join) dispara uma query por livro: o N+1.
     */
    @Query("select b from Book b order by b.id")
    List<Book> findPage(Pageable pageable);

    /**
     * Mesma pagina, mas com {@code join fetch} do autor: uma unica query carrega
     * livros e autores juntos. E a versao corrigida, para efeito de comparacao.
     */
    @Query("select b from Book b join fetch b.author order by b.id")
    List<Book> findPageWithAuthor(Pageable pageable);
}
