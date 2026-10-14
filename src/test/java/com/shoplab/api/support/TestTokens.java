package com.shoplab.api.support;

import com.shoplab.api.security.JjwtService;
import com.shoplab.api.security.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * Tokens para los escenarios de §8.2 (design D7 de auth-jwt-filter). Todos usan el reloj real,
 * igual que la aplicación al validar, y el secreto de prueba TestJwt.SECRET.
 */
public final class TestTokens {

    private static final Duration TTL = Duration.ofHours(1);
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private TestTokens() {
    }

    /** Token válido durante 1 hora, igual al que emite el login. */
    public static String valid(long userId, String email) {
        return serviceAt(Clock.systemUTC(), TestJwt.SECRET).generateToken(userId, email);
    }

    /** Emitido hace 2 horas con TTL de 1 hora: venció hace 1 hora. Firma correcta. */
    public static String expired(long userId, String email) {
        Clock twoHoursAgo = Clock.offset(Clock.systemUTC(), Duration.ofHours(-2));
        return serviceAt(twoHoursAgo, TestJwt.SECRET).generateToken(userId, email);
    }

    /** Bien formado, pero firmado con un secreto que la API no conoce. */
    public static String signedWithOtherSecret(long userId, String email) {
        return serviceAt(Clock.systemUTC(), "another-secret-that-the-api-does-not-know").generateToken(userId, email);
    }

    /** Token válido al que se le cambia el payload (sub = 2) conservando la firma original. */
    public static String tamperedPayload(long userId, String email) {
        return replacePayload(valid(userId, email), userId + 1, email);
    }

    /** Vencido y además alterado: la firma se comprueba antes que la expiración (§8.2). */
    public static String expiredAndTampered(long userId, String email) {
        return replacePayload(expired(userId, email), userId + 1, email);
    }

    /** Token sin firma: cabecera {"alg":"none"} y firma vacía. */
    public static String unsigned(long userId, String email) {
        String header = encode("{\"alg\":\"none\"}");
        return header + "." + payload(userId, email, Instant.now()) + ".";
    }

    /** Firma correcta, pero sub no es un número. */
    public static String nonNumericSubject() {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("abc")
                .claim("email", "standard@shoplab.dev")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(TTL)))
                .signWith(Keys.hmacShaKeyFor(TestJwt.SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
    }

    /** Firma correcta, pero sin el claim email. */
    public static String withoutEmail(long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(TTL)))
                .signWith(Keys.hmacShaKeyFor(TestJwt.SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
    }

    /** Valor de la cabecera Authorization. */
    public static String bearer(String token) {
        return "Bearer " + token;
    }

    private static JjwtService serviceAt(Clock clock, String secret) {
        return new JjwtService(new JwtProperties(secret, TTL), clock);
    }

    private static String replacePayload(String token, long newUserId, String email) {
        String[] parts = token.split("\\.");
        return parts[0] + "." + payload(newUserId, email, Instant.now()) + "." + parts[2];
    }

    private static String payload(long userId, String email, Instant issuedAt) {
        long iat = issuedAt.getEpochSecond();
        return encode("{\"sub\":\"" + userId + "\",\"email\":\"" + email + "\",\"iat\":" + iat
                + ",\"exp\":" + (iat + TTL.toSeconds()) + "}");
    }

    private static String encode(String json) {
        return BASE64_URL.encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
