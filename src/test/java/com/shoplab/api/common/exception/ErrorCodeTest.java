package com.shoplab.api.common.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeTest {

    // Tabla §9.1 de docs/shop-backend-arquitectura.md
    @ParameterizedTest(name = "{0} → {1} \"{2}\"")
    @CsvSource(delimiter = '|', textBlock = """
            VALIDATION_ERROR       | 400 | Request has invalid fields
            INVALID_CREDENTIALS    | 401 | Invalid email or password
            USER_LOCKED            | 423 | User is locked
            EMAIL_ALREADY_EXISTS   | 409 | Email is already registered
            UNDERAGE_USER          | 422 | User must be at least 18 years old
            TERMS_NOT_ACCEPTED     | 422 | Terms and conditions must be accepted
            PRODUCT_NOT_FOUND      | 404 | Product %d not found
            INSUFFICIENT_STOCK     | 409 | Insufficient stock for product %d
            EMPTY_ORDER            | 400 | Order must contain at least one item
            DUPLICATE_ORDER_ITEM   | 400 | Product %d appears more than once in the order
            ORDER_NOT_FOUND        | 404 | Order %d not found
            USER_NOT_FOUND         | 404 | User not found
            AUTH_TOKEN_MISSING     | 401 | Authentication token is missing
            AUTH_TOKEN_MALFORMED   | 401 | Authentication token is malformed
            AUTH_TOKEN_INVALID     | 401 | Authentication token is invalid
            AUTH_TOKEN_EXPIRED     | 401 | Authentication token has expired
            ACCESS_DENIED          | 403 | Access denied
            NOT_FOUND              | 404 | Resource not found
            METHOD_NOT_ALLOWED     | 405 | HTTP method not allowed
            UNSUPPORTED_MEDIA_TYPE | 415 | Content-Type must be application/json
            INTERNAL_ERROR         | 500 | Unexpected error
            FORCED_ERROR           | 500 | Forced error for testing
            """)
    void statusAndMessageMatchTheCatalog(ErrorCode code, int status, String message) {
        assertThat(code.getStatus().value()).isEqualTo(status);
        assertThat(code.getMessage()).isEqualTo(message);
    }

    @Test
    void enumAndErrorResponseSchemaHaveTheSameCodes() throws IOException {
        JsonNode schema = new ObjectMapper().readTree(Path.of("docs/contracts/error-response.schema.json").toFile());
        Set<String> schemaCodes = new HashSet<>();
        schema.at("/properties/code/enum").forEach(node -> schemaCodes.add(node.asText()));

        Set<String> enumCodes = Arrays.stream(ErrorCode.values()).map(Enum::name).collect(Collectors.toSet());

        assertThat(enumCodes)
                .as("Si agregas un ErrorCode, agrégalo también a docs/contracts/error-response.schema.json (y al revés)")
                .isEqualTo(schemaCodes);
    }
}
