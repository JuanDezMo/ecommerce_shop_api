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
    void apiDocsListTheCatalogRoutes() {
        ResponseEntity<String> response = rest.getForEntity("/v3/api-docs", String.class);

        assertThat(response.getBody())
                .contains("\"/api/v1/products\"")
                .contains("\"/api/v1/products/{id}\"");
    }

    @Test
    void apiDocsListTheRegistrationRoute() {
        ResponseEntity<String> response = rest.getForEntity("/v3/api-docs", String.class);

        assertThat(response.getBody()).contains("\"/api/v1/users\"");
    }

    @Test
    void apiDocsListTheLoginRoute() {
        ResponseEntity<String> response = rest.getForEntity("/v3/api-docs", String.class);

        assertThat(response.getBody()).contains("\"/api/v1/auth/login\"");
    }

    @Test
    void profileRouteIsDocumentedWithBearerAuth() throws Exception {
        ResponseEntity<String> response = rest.getForEntity("/v3/api-docs", String.class);

        com.fasterxml.jackson.databind.JsonNode me = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(response.getBody()).path("paths").path("/api/v1/users/me").path("get");
        assertThat(me.isMissingNode()).isFalse();
        // El candado de Swagger UI: la operación declara el esquema bearerAuth
        assertThat(me.path("security").toString()).contains("\"bearerAuth\"");
    }

    @Test
    void orderRoutesAreDocumentedWithBearerAuth() throws Exception {
        ResponseEntity<String> response = rest.getForEntity("/v3/api-docs", String.class);

        com.fasterxml.jackson.databind.JsonNode paths = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(response.getBody()).path("paths");
        assertThat(paths.path("/api/v1/orders").path("post").path("security").toString()).contains("\"bearerAuth\"");
        assertThat(paths.path("/api/v1/orders").path("get").path("security").toString()).contains("\"bearerAuth\"");
        assertThat(paths.path("/api/v1/orders/{id}").path("get").path("security").toString()).contains("\"bearerAuth\"");
    }

    @Test
    void swaggerUiRedirectsToTheUi() {
        ResponseEntity<String> response = rest.getForEntity("/swagger-ui.html", String.class);

        // TestRestTemplate sigue la redirección y llega al HTML de Swagger UI
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Swagger UI");
    }
}
