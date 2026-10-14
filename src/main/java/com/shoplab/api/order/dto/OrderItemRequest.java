package com.shoplab.api.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Un producto del carrito. El precio NO viene de la app: lo pone el backend (§6.3). */
public record OrderItemRequest(
        @NotNull(message = "must not be null")
        Long productId,

        @NotNull(message = "must not be null")
        @Min(value = 1, message = "must be between 1 and 99")
        @Max(value = 99, message = "must be between 1 and 99")
        Integer quantity) {
}
