package com.shoplab.api.product;

import com.shoplab.api.product.dto.ProductResponse;

import java.util.List;

/**
 * Consulta del catálogo. Devuelve DTOs o lanza BusinessException; nunca Optional ni null.
 */
public interface ProductService {

    /** Todos los productos, ordenados por id. */
    List<ProductResponse> findAll();

    /** El producto con ese id, o BusinessException(PRODUCT_NOT_FOUND) si no existe. */
    ProductResponse findById(Long id);
}
