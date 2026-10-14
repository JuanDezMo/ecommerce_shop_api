package com.shoplab.api.product.dto;

import java.math.BigDecimal;

/**
 * Producto tal como lo ve la API. Contrato: docs/contracts/product-response.schema.json.
 * price va en USD con 2 decimales; stock = 0 significa "Agotado" en la app.
 */
public record ProductResponse(
        Long id,
        String sku,
        String name,
        String description,
        BigDecimal price,
        Integer stock,
        String imageKey) {
}
