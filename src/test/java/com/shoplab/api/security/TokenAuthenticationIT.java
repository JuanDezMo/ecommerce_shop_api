package com.shoplab.api.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoplab.api.support.ContractAssertions;
import com.shoplab.api.support.IntegrationTest;
import com.shoplab.api.support.TestTokens;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Escenarios de specs/token-authentication y specs/user-profile contra la aplicación completa:
 * tokens reales del login y los de TestTokens (firmados con el mismo TestJwt.SECRET).
 */
class TokenAuthenticationIT extends IntegrationTest {

    private static final String ME = "/api/v1/users/me";
    private static final String EMAIL = "standard@shoplab.dev";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void deleteRegisteredUsers() {
        jdbc.update("DELETE FROM users WHERE email LIKE '%@registration.it'");
    }

    ResponseEntity<String> get(String path, String authorization) {
        HttpHeaders headers = new HttpHeaders();
        if (authorization != null) {
            headers.set(HttpHeaders.AUTHORIZATION, authorization);
        }
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    ResponseEntity<String> postJson(String path, String json, String authorization) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (authorization != null) {
            headers.set(HttpHeaders.AUTHORIZATION, authorization);
        }
        return rest.postForEntity(path, new HttpEntity<>(json, headers), String.class);
    }

    String loginToken(String email, String password) throws Exception {
        ResponseEntity<String> login = postJson("/api/v1/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}", null);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        return "Bearer " + json(login).get("accessToken").asText();
    }

    static JsonNode json(ResponseEntity<String> response) throws Exception {
        return MAPPER.readTree(response.getBody());
    }

    static void assertRejected(ResponseEntity<String> response, String code, String path) throws Exception {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ContractAssertions.assertMatchesSchema(response.getBody(), "error-response");
        assertThat(json(response).get("code").asText()).isEqualTo(code);
        assertThat(json(response).get("path").asText()).isEqualTo(path);
        assertThat(json(response).get("fieldErrors")).isEmpty();
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    void loginTokenOpensTheProfile() throws Exception {
        ResponseEntity<String> response = get(ME, loginToken(EMAIL, "Secret_123"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ContractAssertions.assertMatchesSchema(response.getBody(), "user-response");
        JsonNode user = json(response);
        assertThat(user.get("id").asLong()).isEqualTo(1);
        assertThat(user.get("email").asText()).isEqualTo(EMAIL);
        assertThat(user.get("firstName").asText()).isEqualTo("Standard");
        assertThat(user.get("phone").isNull()).isTrue();
        assertThat(user.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(response.getBody()).doesNotContain("password");
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    void eachUserSeesOnlyTheirOwnProfile() throws Exception {
        assertThat(json(get(ME, loginToken(EMAIL, "Secret_123"))).get("id").asLong()).isEqualTo(1);
        assertThat(json(get(ME, loginToken("buyer@shoplab.dev", "Secret_123"))).get("id").asLong()).isEqualTo(3);
    }

    @Test
    void registeredUserProfileEqualsTheRegistrationResponse() throws Exception {
        ResponseEntity<String> registered = postJson("/api/v1/users", """
                {"firstName":"Ana","lastName":"Gomez","email":"me@registration.it","password":"Secret_456",
                 "phone":"3001234567","birthDate":"1990-05-20","country":"MX","plan":"PRO","experienceLevel":5,
                 "newsletter":true,"termsAccepted":true}
                """, null);
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> me = get(ME, loginToken("me@registration.it", "Secret_456"));

        assertThat(json(me)).isEqualTo(json(registered));
    }

    static Stream<Arguments> rejectedAuthorizations() {
        return Stream.of(
                Arguments.of("sin cabecera", null, "AUTH_TOKEN_MISSING"),
                Arguments.of("otro esquema", "Basic xyz", "AUTH_TOKEN_MISSING"),
                Arguments.of("Bearer vacío", "Bearer ", "AUTH_TOKEN_MALFORMED"),
                Arguments.of("no es un JWT", "Bearer abc.def", "AUTH_TOKEN_MALFORMED"),
                Arguments.of("otro secreto", TestTokens.bearer(TestTokens.signedWithOtherSecret(1L, EMAIL)), "AUTH_TOKEN_INVALID"),
                Arguments.of("payload alterado", TestTokens.bearer(TestTokens.tamperedPayload(1L, EMAIL)), "AUTH_TOKEN_INVALID"),
                Arguments.of("sin firma", TestTokens.bearer(TestTokens.unsigned(1L, EMAIL)), "AUTH_TOKEN_INVALID"),
                Arguments.of("sub no numérico", TestTokens.bearer(TestTokens.nonNumericSubject()), "AUTH_TOKEN_INVALID"),
                Arguments.of("expirado", TestTokens.bearer(TestTokens.expired(1L, EMAIL)), "AUTH_TOKEN_EXPIRED"),
                Arguments.of("expirado y alterado", TestTokens.bearer(TestTokens.expiredAndTampered(1L, EMAIL)), "AUTH_TOKEN_INVALID"));
    }

    @ParameterizedTest(name = "{0} → {2}")
    @MethodSource("rejectedAuthorizations")
    void profileRejectsEveryBadToken(String scenario, String authorization, String expectedCode) throws Exception {
        assertRejected(get(ME, authorization), expectedCode, ME);
    }

    @Test
    void tokenOfADeletedUserIsInvalid() throws Exception {
        postJson("/api/v1/users", """
                {"firstName":"Ana","lastName":"Gomez","email":"gone@registration.it","password":"Secret_456",
                 "birthDate":"1990-05-20","country":"CO","plan":"BASIC","experienceLevel":3,"termsAccepted":true}
                """, null);
        String token = loginToken("gone@registration.it", "Secret_456");
        jdbc.update("DELETE FROM users WHERE email = 'gone@registration.it'");

        assertRejected(get(ME, token), "AUTH_TOKEN_INVALID", ME);
    }

    @Test
    void brokenTokenIsIgnoredOnPublicRoutes() throws Exception {
        assertThat(get("/api/v1/products", "Bearer abc.def").getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> login = postJson("/api/v1/auth/login",
                "{\"email\":\"standard@shoplab.dev\",\"password\":\"Secret_123\"}",
                TestTokens.bearer(TestTokens.expired(1L, EMAIL)));
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void unknownProtectedRouteIs401WithoutTokenAnd404WithToken() throws Exception {
        assertRejected(get("/api/v1/does-not-exist", null), "AUTH_TOKEN_MISSING", "/api/v1/does-not-exist");

        ResponseEntity<String> withToken = get("/api/v1/does-not-exist", loginToken(EMAIL, "Secret_123"));
        assertThat(withToken.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(json(withToken).get("code").asText()).isEqualTo("NOT_FOUND");
    }

    @Test
    void unknownRouteInsidePublicRouteIs404WithoutToken() throws Exception {
        ResponseEntity<String> response = get("/api/v1/products/1/reviews", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(json(response).get("code").asText()).isEqualTo("NOT_FOUND");
    }

    @Test
    void testRoutesWithoutTestingProfileAre404NotUnauthorized() throws Exception {
        ResponseEntity<String> response = postJson("/api/v1/test/reset", "{}", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(json(response).get("code").asText()).isEqualTo("NOT_FOUND");
    }

    @Test
    void chaosHeadersAreIgnoredWithoutTestingProfile() {
        // specs/testing-support: sin el perfil testing no hay ChaosFilter
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Force-Error", "500");

        ResponseEntity<String> response = rest.exchange("/api/v1/products", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
