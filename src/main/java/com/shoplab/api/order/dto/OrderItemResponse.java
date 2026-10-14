package com.shoplab.api.order.dto;

import java.math.BigDecimal;

/** subtotal = unitPrice × quantity. Los tests de API pueden verificar la cuenta. */
public record OrderItemResponse(
        Long productId,
        String sku,
        String name,
        String imageKey,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal) {
}
