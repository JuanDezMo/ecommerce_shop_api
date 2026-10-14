package com.shoplab.api.testsupport.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Locale;

/** Body de POST /api/v1/test/tokens/expired. El email se normaliza igual que en el login (§6.3). */
public record ExpiredTokenRequest(
        @NotBlank(message = "must not be blank")
        String email) {

    public ExpiredTokenRequest {
        email = (email == null) ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
