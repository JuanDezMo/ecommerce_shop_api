package com.shoplab.api.common.dto;

import java.time.Instant;
import java.util.List;

/**
 * Formato único de todas las respuestas de error (§9 y docs/contracts/error-response.schema.json).
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        List<FieldErrorResponse> fieldErrors) {
}
