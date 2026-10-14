package com.shoplab.api.user;

import com.shoplab.api.user.dto.UserResponse;
import org.springframework.stereotype.Component;

/**
 * Convierte User → UserResponse. El passwordHash se queda fuera a propósito.
 */
@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhone(),
                user.getBirthDate(),
                user.getCountry(),
                user.getPlan(),
                user.getExperienceLevel(),
                user.getNewsletter(),
                user.getStatus(),
                user.getTermsAcceptedAt(),
                user.getCreatedAt());
    }
}
