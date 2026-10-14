package com.shoplab.api.auth.dto;

/**
 * Respuesta del login. Contrato: docs/contracts/login-response.schema.json.
 * tokenType siempre es "Bearer"; expiresIn está en segundos.
 */
public record LoginResponse(String accessToken, String tokenType, long expiresIn) {
}
