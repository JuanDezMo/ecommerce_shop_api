package com.shoplab.api.config;

import com.shoplab.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiIT extends IntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void apiDocsArePublicAndDeclareBearerAuth() {
        ResponseEntity<String> response = rest.getForEntity("/v3/api-docs", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("\"title\":\"ShopLab API\"")
                .contains("\"bearerAuth\"")
                .contains("\"scheme\":\"bearer\"")
                .contains("\"bearerFormat\":\"JWT\"");
    }

    @Test
    void swaggerUiRedirectsToTheUi() {
        ResponseEntity<String> response = rest.getForEntity("/swagger-ui.html", String.class);

        // TestRestTemplate sigue la redirección y llega al HTML de Swagger UI
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Swagger UI");
    }
}
