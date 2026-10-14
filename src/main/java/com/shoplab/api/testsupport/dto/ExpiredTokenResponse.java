package com.shoplab.api.testsupport.dto;

/**
 * Token ya expirado. Contrato: docs/contracts/expired-token-response.schema.json.
 * Sin expiresIn: un token expirado no tiene vida restante (decisión de la revisión del change).
 */
public record ExpiredTokenResponse(String accessToken, String tokenType) {
}
