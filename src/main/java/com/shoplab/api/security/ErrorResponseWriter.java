package com.shoplab.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoplab.api.common.dto.ErrorResponse;
import com.shoplab.api.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * Escribe un ErrorResponse desde un filtro, donde el GlobalExceptionHandler no llega (§8.2).
 * Lo usan el entry point (401), el access denied handler (403) y el ChaosFilter del perfil testing.
 */
public final class ErrorResponseWriter {

    private ErrorResponseWriter() {
    }

    /** Usa el status del ErrorCode. */
    public static void write(HttpServletResponse response, ObjectMapper objectMapper, ErrorCode code,
                             HttpServletRequest request) throws IOException {
        write(response, objectMapper, code, code.getStatus().value(), request);
    }

    /** Status explícito: FORCED_ERROR se declara con 500 pero responde el de la cabecera X-Force-Error (§9.1). */
    public static void write(HttpServletResponse response, ObjectMapper objectMapper, ErrorCode code, int status,
                             HttpServletRequest request) throws IOException {
        ErrorResponse body = new ErrorResponse(Instant.now(), status, code.name(),
                code.getMessage(), request.getRequestURI(), List.of());
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
