package com.shoplab.api.health;

import com.shoplab.api.support.ContractAssertions;
import com.shoplab.api.support.IntegrationTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class HealthIT extends IntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void healthIsPublicAndUpWithoutDetails() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("{\"status\":\"UP\"}");
    }

    // specs/service-health (modificada por auth-jwt-filter): solo /actuator/health es pública,
    // así que el resto responde 401 sin token y 404 con un token válido
    @ParameterizedTest
    @ValueSource(strings = {"/actuator/env", "/actuator/beans", "/actuator/info", "/actuator/metrics"})
    void otherActuatorEndpointsRequireATokenFirst(String path) {
        ResponseEntity<String> response = rest.getForEntity(path, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("\"code\":\"AUTH_TOKEN_MISSING\"");
        ContractAssertions.assertMatchesSchema(response.getBody(), "error-response");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/env", "/actuator/beans", "/actuator/info", "/actuator/metrics"})
    void otherActuatorEndpointsAreNotExposedEvenWithAToken(String path) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(loginToken());

        ResponseEntity<String> response = rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("\"code\":\"NOT_FOUND\"");
        ContractAssertions.assertMatchesSchema(response.getBody(), "error-response");
    }

    private String loginToken() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> login = rest.postForEntity("/api/v1/auth/login",
                new HttpEntity<>("{\"email\":\"standard@shoplab.dev\",\"password\":\"Secret_123\"}", headers), String.class);
        return new ObjectMapper().readTree(login.getBody()).get("accessToken").asText();
    }
}
