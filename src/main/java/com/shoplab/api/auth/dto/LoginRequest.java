package com.shoplab.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Locale;

/**
 * Body de POST /auth/login. Solo se exige que los campos no estén en blanco (§7.2):
 * sin @Email ni política de contraseña, así cualquier credencial incorrecta responde 401 y no 400.
 */
public record LoginRequest(
        @NotBlank(message = "must not be blank")
        String email,

        @NotBlank(message = "must not be blank")
        String password) {

    public LoginRequest {
        // Se ejecuta al deserializar el JSON, antes de @Valid (§6.3). La contraseña no se toca.
        email = (email == null) ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
