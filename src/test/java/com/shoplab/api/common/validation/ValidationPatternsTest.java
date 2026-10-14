package com.shoplab.api.common.validation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationPatternsTest {

    @ParameterizedTest
    @ValueSource(strings = {"Secret_123", "Abcdef1!", "Z9#zzzzz"})
    void passwordAcceptsValidValues(String value) {
        assertThat(value).matches(ValidationPatterns.PASSWORD);
    }

    @ParameterizedTest
    @ValueSource(strings = {"secret_123", "SECRET_123", "Secret_abc", "Secret123", "Secret 123", "Señal_123"})
    void passwordRejectsMissingClassesSpacesAndNonAscii(String value) {
        assertThat(value).doesNotMatch(ValidationPatterns.PASSWORD);
    }

    @ParameterizedTest
    @ValueSource(strings = {"US", "CO", "MX", "ES", "AR", "CL", "PE"})
    void countryAcceptsSupportedCodes(String value) {
        assertThat(value).matches(ValidationPatterns.COUNTRY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"BR", "co", "COL", ""})
    void countryRejectsOtherValues(String value) {
        assertThat(value).doesNotMatch(ValidationPatterns.COUNTRY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"110111", "SW1A 1AA", "28013", "C1425-ABC"})
    void postalCodeAcceptsValidValues(String value) {
        assertThat(value).matches(ValidationPatterns.POSTAL_CODE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"12", "ABC_123", "12345678901"})
    void postalCodeRejectsInvalidValues(String value) {
        assertThat(value).doesNotMatch(ValidationPatterns.POSTAL_CODE);
    }
}
