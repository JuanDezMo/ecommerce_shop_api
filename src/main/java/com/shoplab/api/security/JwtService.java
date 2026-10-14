package com.shoplab.api.security;

/**
 * Emisión de tokens JWT. El change auth-jwt-filter añade parse(token).
 * Recibe id y email (no un User) para que security no dependa del paquete user.
 */
public interface JwtService {

    /** Token HS256 con sub = userId, email, iat y exp = iat + jwt.ttl. */
    String generateToken(Long userId, String email);
}
