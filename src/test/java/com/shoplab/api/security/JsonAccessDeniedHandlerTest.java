package com.shoplab.api.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.shoplab.api.support.ContractAssertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reservado: hoy no hay roles, pero un 403 también sale con el formato único (§4).
 */
class JsonAccessDeniedHandlerTest {

    private final JsonAccessDeniedHandler handler = new JsonAccessDeniedHandler(JwtAuthenticationEntryPointTest.OBJECT_MAPPER);

    @Test
    void accessDeniedIs403InJson() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(new MockHttpServletRequest("GET", "/api/v1/orders"), response, new AccessDeniedException("denied"));

        String body = response.getContentAsString();
        ContractAssertions.assertMatchesSchema(body, "error-response");
        JsonNode json = JwtAuthenticationEntryPointTest.OBJECT_MAPPER.readTree(body);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(json.get("code").asText()).isEqualTo("ACCESS_DENIED");
        assertThat(json.get("message").asText()).isEqualTo("Access denied");
        assertThat(json.get("path").asText()).isEqualTo("/api/v1/orders");
    }
}
