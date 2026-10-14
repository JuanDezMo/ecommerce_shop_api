package com.shoplab.api.common.dto;

/**
 * Un campo inválido dentro de un ErrorResponse. "field" usa la notación de Spring: shipping.city, items[0].quantity.
 */
public record FieldErrorResponse(String field, String message) {
}
