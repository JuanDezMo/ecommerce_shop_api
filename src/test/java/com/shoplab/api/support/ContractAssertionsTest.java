package com.shoplab.api.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContractAssertionsTest {

    private static final String VALID_ERROR = """
            {
              "timestamp": "2026-10-11T15:04:05Z",
              "status": 404,
              "code": "NOT_FOUND",
              "message": "Resource not found",
              "path": "/api/v1/nothing",
              "fieldErrors": []
            }
            """;

    @Test
    void validJsonMatchesSchema() {
        assertThatCode(() -> ContractAssertions.assertMatchesSchema(VALID_ERROR, "error-response"))
                .doesNotThrowAnyException();
    }

    @Test
    void extraFieldFailsWithReadableMessage() {
        String withExtraField = VALID_ERROR.replace("\"fieldErrors\": []", "\"fieldErrors\": [], \"trace\": \"boom\"");

        assertThatThrownBy(() -> ContractAssertions.assertMatchesSchema(withExtraField, "error-response"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("error-response")
                .hasMessageContaining("trace");
    }

    @Test
    void unknownSchemaFails() {
        assertThatThrownBy(() -> ContractAssertions.assertMatchesSchema(VALID_ERROR, "does-not-exist"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
