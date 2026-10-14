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
 * Escribe un ErrorResponse desde la cadena de seguridad, donde el GlobalExceptionHandler no llega (§8.2).
 * Lo usan el entry point (401) y el access denied handler (403).
 */
final class ErrorResponseWriter {

    private ErrorResponseWriter() {
    }

    static void write(HttpServletResponse response, ObjectMapper objectMapper, ErrorCode code,
                      HttpServletRequest request) throws IOException {
        ErrorResponse body = new ErrorResponse(Instant.now(), code.getStatus().value(), code.name(),
                code.getMessage(), request.getRequestURI(), List.of());
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
