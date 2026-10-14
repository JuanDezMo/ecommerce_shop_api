package com.shoplab.api.user;

import com.shoplab.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mapeo de User contra la tabla users de V1 y los usuarios semilla de V4 (design D4).
 */
class UserRepositoryIT extends IntegrationTest {

    @Autowired
    UserRepository userRepository;

    @Test
    void existsByEmailFindsSeedUsersOnly() {
        assertThat(userRepository.existsByEmail("standard@shoplab.dev")).isTrue();
        assertThat(userRepository.existsByEmail("nobody@registration.it")).isFalse();
    }

    @Test
    void findByIdMapsEveryColumn() {
        User user = userRepository.findById(1L).orElseThrow();

        assertThat(user.getEmail()).isEqualTo("standard@shoplab.dev");
        assertThat(user.getPasswordHash()).startsWith("$2a$10$");
        assertThat(user.getFirstName()).isEqualTo("Standard");
        assertThat(user.getLastName()).isEqualTo("User");
        assertThat(user.getPhone()).isNull();
        assertThat(user.getBirthDate()).isEqualTo(LocalDate.of(1990, 5, 20));
        assertThat(user.getCountry()).isEqualTo("CO");
        assertThat(user.getPlan()).isEqualTo(Plan.BASIC);
        assertThat(user.getExperienceLevel()).isEqualTo(3);
        assertThat(user.getNewsletter()).isFalse();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getTermsAcceptedAt()).isNotNull();
        assertThat(user.getCreatedAt()).isNotNull();
    }

    @Test
    void lockedSeedUserIsLocked() {
        assertThat(userRepository.findById(2L).orElseThrow().getStatus()).isEqualTo(UserStatus.LOCKED);
    }
}
