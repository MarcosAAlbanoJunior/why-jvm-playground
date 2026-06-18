package io.whyjvm.playground.catalog;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Livro inexistente: vira 404, e nao um incidente de erro do why-jvm. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class BookNotFoundException extends RuntimeException {

    public BookNotFoundException(Long id) {
        super("Livro %d nao encontrado".formatted(id));
    }
}
