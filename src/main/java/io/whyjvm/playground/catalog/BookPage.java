package io.whyjvm.playground.catalog;

import java.util.List;

/**
 * Resposta de uma listagem de livros.
 *
 * @param strategy   estrategia de leitura usada ({@code naive} ou {@code optimized})
 * @param requested  quantos livros foram pedidos
 * @param returned   quantos livros voltaram
 * @param books      os livros, com autor resolvido
 */
public record BookPage(String strategy, int requested, int returned, List<BookView> books) {
}
