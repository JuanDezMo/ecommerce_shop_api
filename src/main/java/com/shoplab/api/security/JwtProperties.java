package com.shoplab.api.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Configuración del JWT (§8.4): jwt.secret viene de la variable de entorno JWT_SECRET y jwt.ttl es la duración.
 * El compact constructor valida el secreto al arrancar (fail fast): si falta o es corto, la aplicación no levanta.
 */
@ConfigurationProperties("jwt")
public record JwtProperties(String secret, Duration ttl) {

    // HS256 necesita una clave de al menos 256 bits
    private static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("jwt.secret (JWT_SECRET) is required");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes");
        }
        if (ttl == null) {
            ttl = Duration.ofHours(1);
        }
    }
}
