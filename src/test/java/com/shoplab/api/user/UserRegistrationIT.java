package com.shoplab.api.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shoplab.api.support.ContractAssertions;
import com.shoplab.api.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Escenarios de specs/user-registration de punta a punta.
 * TestRestTemplate no envía Authorization: el registro debe seguir siendo público tras auth-jwt-filter.
 * Los emails usan el dominio @registration.it y se borran después de cada test (design D6),
 * porque SeedDataIT exige que solo existan los 3 usuarios semilla.
 */
class UserRegistrationIT extends IntegrationTest {

    private static final String URL = "/api/v1/users";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    @AfterEach
    void deleteRegisteredUsers() {
        jdbc.update("DELETE FROM users WHERE email LIKE '%@registration.it'");
    }

    static ObjectNode validBody(String email) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("firstName", "Ana");
        body.put("lastName", "Gomez");
        body.put("email", email);
        body.put("password", "Secret_123");
        body.put("phone", "3001234567");
        body.put("birthDate", "1990-05-20");
        body.put("country", "CO");
        body.put("plan", "PRO");
        body.put("experienceLevel", 4);
        body.put("newsletter", true);
        body.put("termsAccepted", true);
        return body;
    }

    ResponseEntity<String> register(JsonNode body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.postForEntity(URL, new HttpEntity<>(body.toString(), headers), String.class);
    }

    static JsonNode json(ResponseEntity<String> response) throws Exception {
        return MAPPER.readTree(response.getBody());
    }

    Integer countUsers(String email) {
        return jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, email);
    }

    @Test
    void validRegistrationIs201AndMatchesTheContract() throws Exception {
        ResponseEntity<String> response = register(validBody("ana@registration.it"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ContractAssertions.assertMatchesSchema(response.getBody(), "user-response");
        JsonNode user = json(response);
        assertThat(user.get("id").asLong()).isGreaterThan(3);
        assertThat(user.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(user.get("plan").asText()).isEqualTo("PRO");
        assertThat(user.get("phone").asText()).isEqualTo("3001234567");
        assertThat(user.get("newsletter").asBoolean()).isTrue();
        assertThat(user.get("createdAt").asText()).isEqualTo(user.get("termsAcceptedAt").asText()).endsWith("Z");
        assertThat(response.getBody()).doesNotContain("password").doesNotContain("Secret_123");
    }

    @Test
    void passwordIsStoredAsBcryptHash() {
        register(validBody("hash@registration.it"));

        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class,
                "hash@registration.it");
        assertThat(hash).isNotEqualTo("Secret_123");
        assertThat(passwordEncoder.matches("Secret_123", hash)).isTrue();
    }

    @Test
    void optionalFieldsDefaultToNullAndFalse() throws Exception {
        ObjectNode body = validBody("optional@registration.it");
        body.remove("phone");
        body.remove("newsletter");

        JsonNode user = json(register(body));

        assertThat(user.get("phone").isNull()).isTrue();
        assertThat(user.get("newsletter").asBoolean()).isFalse();
    }

    @Test
    void emailIsStoredNormalized() throws Exception {
        ResponseEntity<String> response = register(validBody("  Ana.Gomez@Registration.IT "));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(json(response).get("email").asText()).isEqualTo("ana.gomez@registration.it");
        assertThat(countUsers("ana.gomez@registration.it")).isEqualTo(1);
    }

    @Test
    void registeringTheSameEmailTwiceIs409() throws Exception {
        register(validBody("twice@registration.it"));

        ResponseEntity<String> second = register(validBody("TWICE@registration.it"));

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ContractAssertions.assertMatchesSchema(second.getBody(), "error-response");
        assertThat(json(second).get("code").asText()).isEqualTo("EMAIL_ALREADY_EXISTS");
        assertThat(json(second).get("message").asText()).isEqualTo("Email is already registered");
        assertThat(countUsers("twice@registration.it")).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"standard@shoplab.dev", "Standard@ShopLab.dev"})
    void seedUserEmailIs409(String email) throws Exception {
        ResponseEntity<String> response = register(validBody(email));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json(response).get("code").asText()).isEqualTo("EMAIL_ALREADY_EXISTS");
    }

    @Test
    void underageIs422() throws Exception {
        ObjectNode body = validBody("kid@registration.it");
        // Siempre menor: nació hace 10 años, sea cual sea el día en que corra el test
        body.put("birthDate", LocalDate.now().minusYears(10).toString());

        ResponseEntity<String> response = register(body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        ContractAssertions.assertMatchesSchema(response.getBody(), "error-response");
        assertThat(json(response).get("code").asText()).isEqualTo("UNDERAGE_USER");
        assertThat(json(response).get("message").asText()).isEqualTo("User must be at least 18 years old");
        assertThat(countUsers("kid@registration.it")).isZero();
    }

    @Test
    void termsNotAcceptedIs422() throws Exception {
        ObjectNode body = validBody("noterms@registration.it");
        body.put("termsAccepted", false);

        ResponseEntity<String> response = register(body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json(response).get("code").asText()).isEqualTo("TERMS_NOT_ACCEPTED");
        assertThat(countUsers("noterms@registration.it")).isZero();
    }

    @Test
    void validationErrorDoesNotCreateTheUser() throws Exception {
        ObjectNode body = validBody("invalid@registration.it");
        body.put("password", "abc");

        ResponseEntity<String> response = register(body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json(response).get("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(countUsers("invalid@registration.it")).isZero();
    }

    @Test
    void listingUsersIs405() throws Exception {
        ResponseEntity<String> response = rest.getForEntity(URL, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        ContractAssertions.assertMatchesSchema(response.getBody(), "error-response");
        assertThat(json(response).get("code").asText()).isEqualTo("METHOD_NOT_ALLOWED");
    }
}
