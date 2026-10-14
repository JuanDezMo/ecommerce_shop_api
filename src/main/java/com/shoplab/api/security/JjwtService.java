package com.shoplab.api.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Implementación de JwtService con jjwt 0.12. Es la ÚNICA clase que conoce jjwt (§8.2).
 * El Clock inyectado permite fijar iat en los tests y crear tokens ya expirados (perfil testing).
 */
@Service
public class JjwtService implements JwtService {

    private final SecretKey key;
    private final Duration ttl;
    private final Clock clock;

    public JjwtService(JwtProperties properties, Clock clock) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.ttl = properties.ttl();
        this.clock = clock;
    }

    @Override
    public String generateToken(Long userId, String email) {
        Instant issuedAt = Instant.now(clock);
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(ttl)))
                // Algoritmo fijo: con un secreto largo, jjwt elegiría HS384 o HS512 por su cuenta (design D4)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}
