package com.shoplab.api.testsupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoplab.api.support.ContractAssertions;
import com.shoplab.api.support.IntegrationTest;
import com.shoplab.api.support.SeedData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Escenarios HTTP de specs/testing-support con el perfil testing activo (como en docker compose).
 */
@ActiveProfiles("testing")
class TestSupportIT extends IntegrationTest {

    static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void restoreSeed() {
        SeedData.restoreOrdersAndStock(jdbc);
    }

    // ---------- helpers ----------

    ResponseEntity<String> exchange(HttpMethod method, String path, String json, HttpHeaders extra) {
        HttpHeaders headers = new HttpHeaders();
        if (json != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        if (extra != null) {
            headers.addAll(extra);
        }
        return rest.exchange(path, method, new HttpEntity<>(json, headers), String.class);
    }

    static HttpHeaders headers(String... nameValuePairs) {
        HttpHeaders headers = new HttpHeaders();
        for (int i = 0; i < nameValuePairs.length; i += 2) {
            headers.set(nameValuePairs[i], nameValuePairs[i + 1]);
        }
        return headers;
    }

    static JsonNode json(ResponseEntity<String> response) throws Exception {
        return MAPPER.readTree(response.getBody());
    }

    String login(String email, String password) throws Exception {
        ResponseEntity<String> login = exchange(HttpMethod.POST, "/api/v1/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}", null);
        return json(login).get("accessToken").asText();
    }

    ResponseEntity<String> registerUser(String email) {
        return exchange(HttpMethod.POST, "/api/v1/users", """
                {"firstName":"Ana","lastName":"Gomez","email":"%s","password":"Secret_456",
                 "birthDate":"1990-05-20","country":"CO","plan":"BASIC","experienceLevel":3,"termsAccepted":true}
                """.formatted(email), null);
    }

    Integer count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    // ---------- reset ----------

    @Nested
    class Reset {

        @Test
        void resetRestoresTheSeedAfterChanges() throws Exception {
            registerUser("reset@registration.it");
            String buyer = login("buyer@shoplab.dev", "Secret_123");
            ResponseEntity<String> order = exchange(HttpMethod.POST, "/api/v1/orders", """
                    {"items":[{"productId":5,"quantity":1}],"paymentMethod":"CARD",
                     "shipping":{"address":"Calle 10 #20-30","city":"Bogotá","postalCode":"110111","country":"CO"}}
                    """, headers("Authorization", "Bearer " + buyer));
            assertThat(order.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            ResponseEntity<String> reset = exchange(HttpMethod.POST, "/api/v1/test/reset", null, null);

            assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(reset.getBody()).isNull();
            assertThat(count("SELECT count(*) FROM users WHERE email = 'reset@registration.it'")).isZero();
            assertThat(count("SELECT stock FROM products WHERE id = 5")).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM orders WHERE user_id = 3")).isEqualTo(2);
        }

        @Test
        void firstUserAfterResetGetsId4() throws Exception {
            registerUser("before@registration.it");
            exchange(HttpMethod.POST, "/api/v1/test/reset", null, null);

            ResponseEntity<String> registered = registerUser("after@registration.it");

            assertThat(json(registered).get("id").asLong()).isEqualTo(4);
        }

        @Test
        void resetTwiceIsFine() {
            assertThat(exchange(HttpMethod.POST, "/api/v1/test/reset", null, null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(exchange(HttpMethod.POST, "/api/v1/test/reset", null, null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(count("SELECT count(*) FROM users")).isEqualTo(3);
        }

        @Test
        void getResetIs405() throws Exception {
            ResponseEntity<String> response = exchange(HttpMethod.GET, "/api/v1/test/reset", null, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
            assertThat(json(response).get("code").asText()).isEqualTo("METHOD_NOT_ALLOWED");
        }
    }

    // ---------- token expirado ----------

    @Nested
    class ExpiredToken {

        ResponseEntity<String> requestToken(String body) {
            return exchange(HttpMethod.POST, "/api/v1/test/tokens/expired", body, null);
        }

        @Test
        void expiredTokenIsRejectedAsExpired() throws Exception {
            ResponseEntity<String> response = requestToken("{\"email\":\"standard@shoplab.dev\"}");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            ContractAssertions.assertMatchesSchema(response.getBody(), "expired-token-response");
            String token = json(response).get("accessToken").asText();

            ResponseEntity<String> me = exchange(HttpMethod.GET, "/api/v1/users/me", null,
                    headers("Authorization", "Bearer " + token));
            assertThat(me.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(json(me).get("code").asText()).isEqualTo("AUTH_TOKEN_EXPIRED");
        }

        @Test
        void emailIsNormalized() {
            assertThat(requestToken("{\"email\":\"  Standard@ShopLab.dev \"}").getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void unknownEmailIs404() throws Exception {
            ResponseEntity<String> response = requestToken("{\"email\":\"nobody@shoplab.dev\"}");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            ContractAssertions.assertMatchesSchema(response.getBody(), "error-response");
            assertThat(json(response).get("code").asText()).isEqualTo("USER_NOT_FOUND");
            assertThat(json(response).get("message").asText()).isEqualTo("User not found");
        }

        @Test
        void missingOrBlankEmailIs400() throws Exception {
            for (String body : new String[]{"{}", "{\"email\":\"  \"}"}) {
                ResponseEntity<String> response = requestToken(body);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(json(response).get("fieldErrors")).isEqualTo(
                        MAPPER.readTree("[{\"field\":\"email\",\"message\":\"must not be blank\"}]"));
            }
        }
    }

    // ---------- cabeceras de caos ----------

    @Nested
    class Chaos {

        long millisOf(Runnable call) {
            long start = System.nanoTime();
            call.run();
            return (System.nanoTime() - start) / 1_000_000;
        }

        @Test
        void delayWaitsAndThenResponds() {
            ResponseEntity<String>[] holder = new ResponseEntity[1];

            long elapsed = millisOf(() -> holder[0] = exchange(HttpMethod.GET, "/api/v1/products", null, headers("X-Delay", "500")));

            assertThat(holder[0].getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(elapsed).isGreaterThanOrEqualTo(500);
        }

        @Test
        void invalidDelayIsIgnored() {
            long elapsed = millisOf(() -> exchange(HttpMethod.GET, "/api/v1/products", null, headers("X-Delay", "abc")));

            // Sin retraso: muy por debajo de cualquier valor que se hubiera aplicado
            assertThat(elapsed).isLessThan(2000);
        }

        @Test
        void forcedErrorOnPublicRoute() throws Exception {
            ResponseEntity<String> response = exchange(HttpMethod.GET, "/api/v1/products", null, headers("X-Force-Error", "500"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            ContractAssertions.assertMatchesSchema(response.getBody(), "error-response");
            JsonNode body = json(response);
            assertThat(body.get("code").asText()).isEqualTo("FORCED_ERROR");
            assertThat(body.get("message").asText()).isEqualTo("Forced error for testing");
            assertThat(body.get("path").asText()).isEqualTo("/api/v1/products");
            assertThat(body.get("fieldErrors")).isEmpty();
        }

        @Test
        void forcedErrorDoesNotCreateTheOrder() throws Exception {
            String token = login("standard@shoplab.dev", "Secret_123");
            int ordersBefore = count("SELECT count(*) FROM orders");

            ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/orders", """
                    {"items":[{"productId":1,"quantity":1}],"paymentMethod":"CARD",
                     "shipping":{"address":"Calle 10 #20-30","city":"Bogotá","postalCode":"110111","country":"CO"}}
                    """, headers("Authorization", "Bearer " + token, "X-Force-Error", "503"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(json(response).get("code").asText()).isEqualTo("FORCED_ERROR");
            assertThat(count("SELECT count(*) FROM orders")).isEqualTo(ordersBefore);
            assertThat(count("SELECT stock FROM products WHERE id = 1")).isEqualTo(10);
        }

        @Test
        void invalidForcedErrorValuesAreIgnored() {
            for (String value : new String[]{"200", "abc", "700"}) {
                ResponseEntity<String> response = exchange(HttpMethod.GET, "/api/v1/products", null, headers("X-Force-Error", value));

                assertThat(response.getStatusCode()).as("X-Force-Error: " + value).isEqualTo(HttpStatus.OK);
            }
        }

        @Test
        void headersRunBeforeSecurity() throws Exception {
            // Ruta protegida y sin token: gana el error forzado (418), no el 401
            ResponseEntity<String> response = exchange(HttpMethod.GET, "/api/v1/users/me", null, headers("X-Force-Error", "418"));

            assertThat(response.getStatusCode().value()).isEqualTo(418);
            assertThat(json(response).get("code").asText()).isEqualTo("FORCED_ERROR");
        }

        @Test
        void delayAndErrorTogetherWaitThenFail() throws Exception {
            ResponseEntity<String>[] holder = new ResponseEntity[1];

            long elapsed = millisOf(() -> holder[0] = exchange(HttpMethod.GET, "/api/v1/products", null,
                    headers("X-Delay", "300", "X-Force-Error", "502")));

            assertThat(holder[0].getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
            assertThat(json(holder[0]).get("code").asText()).isEqualTo("FORCED_ERROR");
            assertThat(elapsed).isGreaterThanOrEqualTo(300);
        }
    }
}
