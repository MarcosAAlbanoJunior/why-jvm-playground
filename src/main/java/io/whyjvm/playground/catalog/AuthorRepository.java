package io.whyjvm.playground.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acesso a {@link Author}. */
public interface AuthorRepository extends JpaRepository<Author, Long> {
}
