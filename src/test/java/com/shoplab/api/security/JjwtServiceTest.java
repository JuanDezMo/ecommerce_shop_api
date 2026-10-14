package com.shoplab.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Emisión del token (§8.4, design D4) con un reloj fijo. El test verifica la firma con jjwt directamente,
 * porque JwtService.parse llega con el change auth-jwt-filter.
 */
class JjwtServiceTest {

    static final String SECRET = "0123456789abcdef0123456789abcdef-test";
    static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");

    static JwtService serviceWith(String secret) {
        return new JjwtService(new JwtProperties(secret, Duration.ofHours(1)), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    static Jws<Claims> verify(String token, String secret) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
                .verifyWith(key)
                .clock(() -> Date.from(NOW))   // el token es "de ahora" para el parser, aunque el test corra otro día
                .build()
                .parseSignedClaims(token);
    }

    @Test
    void tokenHasThreeParts() {
        assertThat(serviceWith(SECRET).generateToken(1L, "standard@shoplab.dev").split("\\.")).hasSize(3);
    }

    @Test
    void tokenIsSignedWithHs256AndCarriesTheExpectedClaims() {
        String token = serviceWith(SECRET).generateToken(1L, "standard@shoplab.dev");

        Jws<Claims> jws = verify(token, SECRET);
        JwsHeader header = jws.getHeader();
        Claims claims = jws.getPayload();

        assertThat(header.getAlgorithm()).isEqualTo("HS256");
        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.get("email", String.class)).isEqualTo("standard@shoplab.dev");
        assertThat(claims.getIssuedAt().toInstant()).isEqualTo(NOW);
        assertThat(claims.getExpiration().toInstant()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        // Solo estos cuatro claims: nada de datos sensibles en el token
        assertThat(claims.keySet()).containsExactlyInAnyOrder("sub", "email", "iat", "exp");
    }

    @Test
    void longSecretStillUsesHs256() {
        // Con 64 bytes, jjwt elegiría HS512 si no se fija el algoritmo
        String secret64 = "x".repeat(64);

        String token = serviceWith(secret64).generateToken(1L, "standard@shoplab.dev");

        assertThat(verify(token, secret64).getHeader().getAlgorithm()).isEqualTo("HS256");
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String token = serviceWith(SECRET).generateToken(1L, "standard@shoplab.dev");

        assertThatThrownBy(() -> verify(token, "another-secret-of-at-least-32-bytes!!"))
                .isInstanceOf(JwtException.class);
    }
}
