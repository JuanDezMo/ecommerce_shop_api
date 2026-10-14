package com.shoplab.api.health;

import com.shoplab.api.support.ContractAssertions;
import com.shoplab.api.support.IntegrationTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
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

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/env", "/actuator/beans", "/actuator/info", "/actuator/metrics"})
    void otherActuatorEndpointsAreNotExposed(String path) {
        ResponseEntity<String> response = rest.getForEntity(path, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("\"code\":\"NOT_FOUND\"");
        ContractAssertions.assertMatchesSchema(response.getBody(), "error-response");
    }
}
