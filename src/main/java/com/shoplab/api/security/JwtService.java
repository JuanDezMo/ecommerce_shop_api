package com.shoplab.api.security;

/**
 * Emisión y validación de tokens JWT.
 * Recibe id y email (no un User) para que security no dependa del paquete user.
 */
public interface JwtService {

    /** Token HS256 con sub = userId, email, iat y exp = iat + jwt.ttl. */
    String generateToken(Long userId, String email);

    /**
     * Valida firma y expiración y devuelve el usuario del token.
     * Si el token no es válido lanza InvalidTokenException con el ErrorCode AUTH_TOKEN_* del motivo.
     */
    AuthUser parse(String token);
}
