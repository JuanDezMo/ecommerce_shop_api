package com.shoplab.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoplab.api.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Responde 401 cuando una ruta protegida no está autenticada.
 * El motivo lo deja JwtAuthenticationFilter en el request; si no hay motivo, es que no llegó token.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ErrorCode code = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE) instanceof ErrorCode reason
                ? reason
                : ErrorCode.AUTH_TOKEN_MISSING;
        ErrorResponseWriter.write(response, objectMapper, code, request);
    }
}
