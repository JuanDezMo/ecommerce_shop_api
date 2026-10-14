package com.shoplab.api.order.dto;

import com.shoplab.api.order.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Body de POST /orders: el carrito completo de la app (§7.4).
 * items NO lleva @NotEmpty a propósito: la lista vacía la rechaza el service con EMPTY_ORDER.
 * Los campos desconocidos (total, unitPrice…) se ignoran: el backend calcula los importes.
 */
public record CreateOrderRequest(
        @NotNull(message = "must not be null")
        List<@NotNull(message = "must not be null") @Valid OrderItemRequest> items,

        @NotNull(message = "must not be null")
        PaymentMethod paymentMethod,

        @NotNull(message = "must not be null")
        @Valid
        ShippingRequest shipping) {
}
