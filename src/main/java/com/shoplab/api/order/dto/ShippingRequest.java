package com.shoplab.api.order.dto;

import com.shoplab.api.common.validation.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Dirección de envío (§7.4). No se valida que el código postal exista ni que corresponda al país.
 */
public record ShippingRequest(
        @NotBlank(message = "must not be blank")
        @Size(min = 5, max = 120, message = "size must be between {min} and {max}")
        String address,

        @NotBlank(message = "must not be blank")
        @Size(min = 2, max = 60, message = "size must be between {min} and {max}")
        String city,

        @NotBlank(message = "must not be blank")
        @Pattern(regexp = ValidationPatterns.POSTAL_CODE, message = "must be 3 to 10 letters, digits, spaces or hyphens")
        String postalCode,

        @NotBlank(message = "must not be blank")
        @Pattern(regexp = ValidationPatterns.COUNTRY, message = "must be one of: US, CO, MX, ES, AR, CL, PE")
        String country) {
}
