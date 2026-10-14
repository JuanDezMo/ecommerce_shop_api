package com.shoplab.api.order.dto;

/** Dirección de envío guardada en la orden. Mismos campos que ShippingRequest, sin validaciones. */
public record ShippingResponse(String address, String city, String postalCode, String country) {
}
