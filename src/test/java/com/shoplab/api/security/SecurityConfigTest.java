package com.shoplab.api.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    // passwordEncoder() no usa las dependencias del filtro JWT: se pasan null
    private final PasswordEncoder encoder = new SecurityConfig(null, null, null).passwordEncoder();

    @Test
    void passwordEncoderIsBcrypt() {
        assertThat(encoder).isInstanceOf(BCryptPasswordEncoder.class);
        assertThat(encoder.encode("Secret_123")).startsWith("$2a$10$").hasSize(60);
    }

    @Test
    void passwordEncoderMatchesOnlyTheOriginalPassword() {
        String hash = encoder.encode("Secret_123");

        assertThat(encoder.matches("Secret_123", hash)).isTrue();
        assertThat(encoder.matches("secret_123", hash)).isFalse();
    }
}
