package com.shoplab.api.user.dto;

import com.shoplab.api.user.Plan;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Normalización en el constructor del record (design D2): ocurre al deserializar, antes de @Valid.
 */
class RegisterUserRequestTest {

    static RegisterUserRequest withEmailAndNewsletter(String email, Boolean newsletter) {
        return new RegisterUserRequest("Ana", "Gomez", email, "Secret_123", null,
                LocalDate.of(1990, 5, 20), "CO", Plan.BASIC, 3, newsletter, true);
    }

    @Test
    void emailIsTrimmedAndLowercased() {
        assertThat(withEmailAndNewsletter("  Ana.Gomez@Example.COM ", false).email())
                .isEqualTo("ana.gomez@example.com");
    }

    @Test
    void nullEmailStaysNull() {
        // null lo rechaza @NotBlank con su mensaje; la normalización no debe fallar
        assertThat(withEmailAndNewsletter(null, false).email()).isNull();
    }

    @Test
    void missingNewsletterDefaultsToFalse() {
        assertThat(withEmailAndNewsletter("ana@example.com", null).newsletter()).isFalse();
    }

    @Test
    void newsletterTrueIsKept() {
        assertThat(withEmailAndNewsletter("ana@example.com", true).newsletter()).isTrue();
    }
}
