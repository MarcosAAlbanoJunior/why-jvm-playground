package io.whyjvm.playground.faults;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cenarios de <b>erro (Tier 1/2)</b>. Cada rota deixa uma excecao subir sem
 * tratamento: o request termina em 500, o span fica com status ERROR e o why-jvm
 * dispara um incidente na hora — sem precisar de aquecimento de baseline.
 */
@RestController
@RequestMapping("/api/faults")
@Tag(name = "Erros (excecoes)", description = "Cenarios Tier 1/2: excecoes nao tratadas -> 500 -> incidente imediato")
public class FaultController {

    private final CheckoutService checkout;

    public FaultController(CheckoutService checkout) {
        this.checkout = checkout;
    }

    /** Divisao por zero: ticket medio de um carrinho vazio ({@link ArithmeticException}). */
    @GetMapping("/checkout")
    @Operation(summary = "Divisao por zero", description = "Ticket medio de um carrinho vazio (ArithmeticException).")
    public Map<String, Long> emptyCartCheckout() {
        long average = checkout.averageTicketCents(List.of());
        return Map.of("averageTicketCents", average);
    }

    /** Desreferencia de nulo: nota fiscal de um pedido sem cliente ({@link NullPointerException}). */
    @GetMapping("/invoice")
    @Operation(summary = "NullPointer", description = "Nota fiscal de um pedido sem cliente (NullPointerException).")
    public Map<String, String> invoiceForMissingCustomer(@RequestParam(defaultValue = "ORD-404") String orderId) {
        return Map.of("email", checkout.invoiceEmail(orderId));
    }

    /** Estado invalido: reserva acima do disponivel ({@link IllegalStateException}). */
    @GetMapping("/inventory")
    @Operation(summary = "Estado invalido", description = "Reserva acima do disponivel (IllegalStateException).")
    public Map<String, Integer> overReserveStock(@RequestParam(defaultValue = "10") int requested,
                                                 @RequestParam(defaultValue = "3") int available) {
        return Map.of("remaining", checkout.reserveStock(requested, available));
    }
}
