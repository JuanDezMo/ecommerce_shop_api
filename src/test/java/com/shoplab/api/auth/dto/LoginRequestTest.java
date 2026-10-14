package com.shoplab.api.auth.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mismo criterio de email que el registro (§6.3): se normaliza antes de @Valid.
 */
class LoginRequestTest {

    @Test
    void emailIsTrimmedAndLowercased() {
        assertThat(new LoginRequest("  Standard@ShopLab.dev ", "Secret_123").email()).isEqualTo("standard@shoplab.dev");
    }

    @Test
    void nullEmailStaysNull() {
        assertThat(new LoginRequest(null, "Secret_123").email()).isNull();
    }

    @Test
    void passwordIsNotTouched() {
        assertThat(new LoginRequest("a@b.com", " Secret_123 ").password()).isEqualTo(" Secret_123 ");
    }
}
