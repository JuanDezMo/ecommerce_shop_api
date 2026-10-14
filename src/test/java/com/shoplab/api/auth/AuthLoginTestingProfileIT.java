package com.shoplab.api.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoplab.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Con el perfil testing (el de docker compose) el token dura 15 minutos (§8.4).
 */
@ActiveProfiles("testing")
class AuthLoginTestingProfileIT extends IntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void tokenLastsFifteenMinutes() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.postForEntity("/api/v1/auth/login",
                new HttpEntity<>("{\"email\":\"standard@shoplab.dev\",\"password\":\"Secret_123\"}", headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new ObjectMapper().readTree(response.getBody()).get("expiresIn").asLong()).isEqualTo(900);
    }
}
