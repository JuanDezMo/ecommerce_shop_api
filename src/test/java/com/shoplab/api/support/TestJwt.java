package com.shoplab.api.support;

/**
 * Secreto JWT SOLO para tests (40 bytes ASCII). La aplicación real lo lee de JWT_SECRET (§8.4);
 * sin él no arranca, así que los @SpringBootTest lo reciben con "properties" (design D5 de auth-login).
 */
public final class TestJwt {

    public static final String SECRET = "test-only-jwt-secret-0123456789-abcdefgh";

    /** Para usar en properties = ... de @SpringBootTest. */
    public static final String SECRET_PROPERTY = "jwt.secret=" + SECRET;

    private TestJwt() {
    }
}
