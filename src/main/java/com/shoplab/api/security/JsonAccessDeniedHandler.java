package com.shoplab.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoplab.api.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Responde 403 ACCESS_DENIED en JSON. Hoy no hay roles: existe "por si acaso" (§4),
 * para que un 403 nunca salga con el formato por defecto de Spring.
 */
@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JsonAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        ErrorResponseWriter.write(response, objectMapper, ErrorCode.ACCESS_DENIED, request);
    }
}
