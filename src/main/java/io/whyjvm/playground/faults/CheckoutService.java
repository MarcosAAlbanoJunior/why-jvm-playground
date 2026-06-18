package io.whyjvm.playground.faults;

import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Regras de finalizacao de compra que escondem bugs classicos de producao.
 *
 * <p>Cada metodo estoura uma excecao diferente a partir de codigo que parece
 * legitimo — e o material que o why-jvm (Tier 2, code-aware) le para explicar a
 * causa raiz a partir da fonte do metodo culpado.
 */
@Service
public class CheckoutService {

    /**
     * Ticket medio do carrinho. O bug: divisao inteira pelo numero de itens sem
     * tratar o carrinho vazio, que estoura {@link ArithmeticException}.
     */
    public long averageTicketCents(List<Long> itemPricesCents) {
        long total = itemPricesCents.stream().mapToLong(Long::longValue).sum();
        return total / itemPricesCents.size();
    }

    /**
     * E-mail para envio da nota fiscal. O bug: assume que sempre existe um cliente
     * cadastrado e desreferencia {@code null}, estourando {@link NullPointerException}.
     */
    public String invoiceEmail(String orderId) {
        Customer customer = findCustomer(orderId);
        return customer.email().toLowerCase();
    }

    /**
     * Reserva de estoque. O bug: deixa a quantidade reservada passar do disponivel,
     * caindo num estado invalido sinalizado por {@link IllegalStateException}.
     */
    public int reserveStock(int requested, int available) {
        int remaining = available - requested;
        if (remaining < 0) {
            throw new IllegalStateException(
                    "Estoque negativo: pedido %d para apenas %d em estoque".formatted(requested, available));
        }
        return remaining;
    }

    /** Simula a busca do cliente: para este pedido de demonstracao, nao existe. */
    private Customer findCustomer(String orderId) {
        return null;
    }

    private record Customer(String email) {
    }
}
