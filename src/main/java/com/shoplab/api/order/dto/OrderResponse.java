package com.shoplab.api.order.dto;

import com.shoplab.api.order.OrderStatus;
import com.shoplab.api.order.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Orden tal como la ve la API. Contrato: docs/contracts/order-response.schema.json.
 * No incluye userId: la orden siempre es del usuario autenticado (§7.5).
 */
public record OrderResponse(
        Long id,
        OrderStatus status,
        PaymentMethod paymentMethod,
        List<OrderItemResponse> items,
        BigDecimal total,
        ShippingResponse shipping,
        Instant createdAt) {
}
