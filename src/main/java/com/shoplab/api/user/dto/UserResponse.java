package com.shoplab.api.user.dto;

import com.shoplab.api.user.Plan;
import com.shoplab.api.user.UserStatus;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Usuario tal como lo ve la API. Contrato: docs/contracts/user-response.schema.json.
 * Nunca incluye la contraseña ni su hash. Solo phone puede ser null.
 */
public record UserResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        String phone,
        LocalDate birthDate,
        String country,
        Plan plan,
        Integer experienceLevel,
        Boolean newsletter,
        UserStatus status,
        Instant termsAcceptedAt,
        Instant createdAt) {
}
