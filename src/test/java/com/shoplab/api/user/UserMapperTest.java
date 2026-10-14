package com.shoplab.api.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.shoplab.api.support.ContractAssertions;
import com.shoplab.api.user.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class UserMapperTest {

    private static final Instant NOW = Instant.parse("2026-10-11T15:04:05Z");

    private final UserMapper mapper = new UserMapper();

    static User ana() {
        // El test vive en el paquete user, así que puede usar el constructor protected (design D4)
        User user = new User();
        ReflectionTestUtils.setField(user, "id", 7L);
        user.setEmail("ana@example.com");
        user.setPasswordHash("$2a$10$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ01234");
        user.setFirstName("Ana");
        user.setLastName("Gomez");
        user.setPhone(null);
        user.setBirthDate(LocalDate.of(1990, 5, 20));
        user.setCountry("CO");
        user.setPlan(Plan.PRO);
        user.setExperienceLevel(4);
        user.setNewsletter(true);
        user.setStatus(UserStatus.ACTIVE);
        user.setTermsAcceptedAt(NOW);
        user.setCreatedAt(NOW);
        return user;
    }

    @Test
    void copiesTheThirteenFields() {
        UserResponse response = mapper.toResponse(ana());

        assertThat(response).isEqualTo(new UserResponse(7L, "ana@example.com", "Ana", "Gomez", null,
                LocalDate.of(1990, 5, 20), "CO", Plan.PRO, 4, true, UserStatus.ACTIVE, NOW, NOW));
    }

    @Test
    void serializedResponseMatchesTheContractAndHasNoPassword() throws Exception {
        // Igual que el ObjectMapper de Spring Boot: fechas ISO-8601, no números
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();

        String json = objectMapper.writeValueAsString(mapper.toResponse(ana()));

        ContractAssertions.assertMatchesSchema(json, "user-response");
        assertThat(json)
                .contains("\"phone\":null")
                .contains("\"createdAt\":\"2026-10-11T15:04:05Z\"")
                .doesNotContain("password")
                .doesNotContain("$2a$10$");
    }
}
