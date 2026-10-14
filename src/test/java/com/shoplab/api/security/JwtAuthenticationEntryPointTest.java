package com.shoplab.api.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.shoplab.api.common.exception.ErrorCode;
import com.shoplab.api.support.ContractAssertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El 401 de las rutas protegidas usa el mismo ErrorResponse que el resto de la API (design D5).
 */
class JwtAuthenticationEntryPointTest {

    static final ObjectMapper OBJECT_MAPPER = Jackson2ObjectMapperBuilder.json()
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)   // igual que Spring Boot
            .build();

    private final JwtAuthenticationEntryPoint entryPoint = new JwtAuthenticationEntryPoint(OBJECT_MAPPER);

    JsonNode commence(MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
        entryPoint.commence(request, response, new InsufficientAuthenticationException("no auth"));
        String body = response.getContentAsString();
        ContractAssertions.assertMatchesSchema(body, "error-response");
        return OBJECT_MAPPER.readTree(body);
    }

    @Test
    void withoutAttributeTheTokenIsMissing() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        JsonNode body = commence(new MockHttpServletRequest("GET", "/api/v1/users/me"), response);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("code").asText()).isEqualTo("AUTH_TOKEN_MISSING");
        assertThat(body.get("message").asText()).isEqualTo("Authentication token is missing");
        assertThat(body.get("path").asText()).isEqualTo("/api/v1/users/me");
        assertThat(body.get("fieldErrors")).isEmpty();
    }

    @Test
    void attributeFromTheFilterDecidesTheCode() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.setAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE, ErrorCode.AUTH_TOKEN_EXPIRED);
        MockHttpServletResponse response = new MockHttpServletResponse();

        JsonNode body = commence(request, response);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(body.get("code").asText()).isEqualTo("AUTH_TOKEN_EXPIRED");
        assertThat(body.get("message").asText()).isEqualTo("Authentication token has expired");
    }
}
