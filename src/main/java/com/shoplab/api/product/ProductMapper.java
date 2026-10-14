package com.shoplab.api.product;

import com.shoplab.api.product.dto.ProductResponse;
import org.springframework.stereotype.Component;

/**
 * Convierte Product → ProductResponse. Solo en esa dirección (bootstrap D1).
 */
@Component
public class ProductMapper {

    public ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStock(),
                product.getImageKey());
    }
}
