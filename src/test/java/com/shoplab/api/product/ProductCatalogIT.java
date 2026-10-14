package com.shoplab.api.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoplab.api.support.ContractAssertions;
import com.shoplab.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Escenarios de specs/product-catalog de punta a punta, contra los datos semilla.
 * TestRestTemplate no envía cabecera Authorization: todo esto debe seguir siendo público
 * cuando auth-jwt-filter reemplace el SecurityConfig provisional.
 * Solo lee: los tests de integración comparten la base de datos.
 */
class ProductCatalogIT extends IntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    TestRestTemplate rest;

    ResponseEntity<String> get(String path) {
        return rest.getForEntity(path, String.class);
    }

    static JsonNode json(ResponseEntity<String> response) throws Exception {
        return MAPPER.readTree(response.getBody());
    }

    @Test
    void listReturnsTheSixSeedProductsOrderedById() throws Exception {
        ResponseEntity<String> response = get("/api/v1/products");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ContractAssertions.assertMatchesSchema(response.getBody(), "product-list");
        assertThat(json(response).findValuesAsText("id")).containsExactly("1", "2", "3", "4", "5", "6");
    }

    @Test
    void detailOfProductOneIsExact() throws Exception {
        ResponseEntity<String> response = get("/api/v1/products/1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ContractAssertions.assertMatchesSchema(response.getBody(), "product-response");
        assertThat(json(response)).isEqualTo(MAPPER.readTree("""
                {"id":1,"sku":"SL-HEAD-001","name":"Wireless Headphones",
                 "description":"Over-ear bluetooth headphones","price":59.99,"stock":10,"imageKey":"headphones"}
                """));
    }

    @Test
    void priceIsANumberWithTwoDecimals() {
        ResponseEntity<String> response = get("/api/v1/products/4");

        ContractAssertions.assertMatchesSchema(response.getBody(), "product-response");
        assertThat(response.getBody()).contains("\"price\":25.00");
    }

    @Test
    void soldOutAndLastUnitAreVisible() throws Exception {
        assertThat(json(get("/api/v1/products/3")).get("stock").asInt()).isZero();
        assertThat(json(get("/api/v1/products/5")).get("stock").asInt()).isEqualTo(1);
    }

    @Test
    void productsHaveOnlyTheSevenContractFields() throws Exception {
        for (JsonNode product : json(get("/api/v1/products"))) {
            assertThat(product.fieldNames()).toIterable()
                    .containsExactlyInAnyOrder("id", "sku", "name", "description", "price", "stock", "imageKey");
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {99, 0, -1})
    void unknownIdIs404WithTheIdInTheMessage(long id) throws Exception {
        ResponseEntity<String> response = get("/api/v1/products/" + id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ContractAssertions.assertMatchesSchema(response.getBody(), "error-response");
        JsonNode body = json(response);
        assertThat(body.get("code").asText()).isEqualTo("PRODUCT_NOT_FOUND");
        assertThat(body.get("message").asText()).isEqualTo("Product " + id + " not found");
        assertThat(body.get("fieldErrors")).isEmpty();
    }

    @Test
    void nonNumericIdIs400() throws Exception {
        ResponseEntity<String> response = get("/api/v1/products/abc");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ContractAssertions.assertMatchesSchema(response.getBody(), "error-response");
        assertThat(json(response).get("fieldErrors")).isEqualTo(
                MAPPER.readTree("[{\"field\":\"id\",\"message\":\"must be an integer\"}]"));
    }

    @Test
    void deleteIs405AndTheProductStillExists() throws Exception {
        ResponseEntity<String> response = rest.exchange("/api/v1/products/1", HttpMethod.DELETE, null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        ContractAssertions.assertMatchesSchema(response.getBody(), "error-response");
        assertThat(json(response).get("code").asText()).isEqualTo("METHOD_NOT_ALLOWED");
        assertThat(get("/api/v1/products/1").getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
