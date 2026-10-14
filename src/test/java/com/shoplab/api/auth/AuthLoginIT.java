package com.shoplab.api.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shoplab.api.support.ContractAssertions;
import com.shoplab.api.support.IntegrationTest;
import com.shoplab.api.support.TestJwt;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Escenarios de specs/user-login de punta a punta, contra los usuarios semilla.
 * Sin cabecera Authorization: el login debe seguir siendo público tras auth-jwt-filter.
 */
class AuthLoginIT extends IntegrationTest {

    private static final String URL = "/api/v1/auth/login";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void deleteRegisteredUsers() {
        // SeedDataIT exige solo los 3 usuarios semilla
        jdbc.update("DELETE FROM users WHERE email LIKE '%@registration.it'");
    }

    ResponseEntity<String> postJson(String url, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.postForEntity(url, new HttpEntity<>(json, headers), String.class);
    }

    ResponseEntity<String> login(String email, String password) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("email", email);
        body.put("password", password);
        return postJson(URL, body.toString());
    }

    static JsonNode json(ResponseEntity<String> response) throws Exception {
        return MAPPER.readTree(response.getBody());
    }

    static Claims claimsOf(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(TestJwt.SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @Test
    void seedUserGetsAValidBearerToken() throws Exception {
        ResponseEntity<String> response = login("standard@shoplab.dev", "Secret_123");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ContractAssertions.assertMatchesSchema(response.getBody(), "login-response");
        JsonNode body = json(response);
        assertThat(body.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(body.get("expiresIn").asLong()).isEqualTo(3600);

        Claims claims = claimsOf(body.get("accessToken").asText());
        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.get("email", String.class)).isEqualTo("standard@shoplab.dev");
        long lifetime = claims.getExpiration().toInstant().getEpochSecond() - claims.getIssuedAt().toInstant().getEpochSecond();
        assertThat(lifetime).isEqualTo(3600);
    }

    @Test
    void emailIsNormalizedBeforeLogin() {
        assertThat(login("  Standard@ShopLab.dev ", "Secret_123").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void lockedUserWithCorrectPasswordIs423() throws Exception {
        ResponseEntity<String> response = login("locked@shoplab.dev", "Secret_123");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
        ContractAssertions.assertMatchesSchema(response.getBody(), "error-response");
        assertThat(json(response).get("code").asText()).isEqualTo("USER_LOCKED");
        assertThat(json(response).get("message").asText()).isEqualTo("User is locked");
    }

    @Test
    void lockedUserWithWrongPasswordIs401() throws Exception {
        ResponseEntity<String> response = login("locked@shoplab.dev", "Wrong_123");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(json(response).get("code").asText()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void everyBadCredentialGetsTheSameResponse() throws Exception {
        List<ResponseEntity<String>> responses = List.of(
                login("standard@shoplab.dev", "Wrong_123"),     // contraseña incorrecta
                login("nobody@shoplab.dev", "Secret_123"),      // email inexistente
                login("standard@shoplab.dev", "A".repeat(100)), // contraseña larguísima (no debe ser 500)
                login("not-an-email", "Secret_123"));           // email sin formato (no debe ser 400)

        for (ResponseEntity<String> response : responses) {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            ContractAssertions.assertMatchesSchema(response.getBody(), "error-response");
            ObjectNode body = (ObjectNode) json(response);
            body.remove("timestamp");   // lo único que puede cambiar
            assertThat(body).isEqualTo(MAPPER.readTree("""
                    {"status":401,"code":"INVALID_CREDENTIALS","message":"Invalid email or password",
                     "path":"/api/v1/auth/login","fieldErrors":[]}
                    """));
        }
    }

    @Test
    void emptyBodyIs400() throws Exception {
        ResponseEntity<String> response = postJson(URL, "{}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json(response).get("fieldErrors")).hasSize(2);
    }

    @Test
    void getIs405() throws Exception {
        ResponseEntity<String> response = rest.getForEntity(URL, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(json(response).get("code").asText()).isEqualTo("METHOD_NOT_ALLOWED");
    }

    @Test
    void newlyRegisteredUserCanLogIn() throws Exception {
        ResponseEntity<String> registered = postJson("/api/v1/users", """
                {"firstName":"Ana","lastName":"Gomez","email":"login@registration.it","password":"Secret_456",
                 "birthDate":"1990-05-20","country":"CO","plan":"BASIC","experienceLevel":3,"termsAccepted":true}
                """);
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long userId = json(registered).get("id").asLong();

        ResponseEntity<String> response = login("login@registration.it", "Secret_456");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(claimsOf(json(response).get("accessToken").asText()).getSubject()).isEqualTo(String.valueOf(userId));
    }
}
