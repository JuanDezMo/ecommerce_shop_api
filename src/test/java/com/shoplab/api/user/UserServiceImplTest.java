package com.shoplab.api.user;

import com.shoplab.api.user.dto.RegisterUserRequest;
import com.shoplab.api.user.dto.UserResponse;
import com.shoplab.api.common.exception.BusinessException;
import com.shoplab.api.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reglas del registro con el repository mockeado, reloj fijo (§6.5) y BCrypt y mapper reales (design D3).
 */
class UserServiceImplTest {

    /** "Hoy" = 2026-10-11 en UTC. */
    static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");

    final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        // saveAndFlush devuelve el mismo usuario con el id que pondría la BD
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 10L);
            return user;
        });
    }

    UserService serviceAt(Instant now) {
        return new UserServiceImpl(userRepository, new UserMapper(), passwordEncoder, Clock.fixed(now, ZoneOffset.UTC));
    }

    static RegisterUserRequest request(String email, LocalDate birthDate, Boolean termsAccepted) {
        return new RegisterUserRequest("Ana", "Gomez", email, "Secret_123", "3001234567",
                birthDate, "CO", Plan.PRO, 4, true, termsAccepted);
    }

    static RegisterUserRequest validRequest() {
        return request("  Ana@Example.com ", LocalDate.of(1990, 5, 20), true);
    }

    @Test
    void registerReturnsAnActiveUserWithClockDates() {
        UserResponse response = serviceAt(NOW).register(validRequest());

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.email()).isEqualTo("ana@example.com");
        assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(response.createdAt()).isEqualTo(NOW);
        assertThat(response.termsAcceptedAt()).isEqualTo(NOW);
        assertThat(response.plan()).isEqualTo(Plan.PRO);
        assertThat(response.experienceLevel()).isEqualTo(4);
        assertThat(response.newsletter()).isTrue();
        assertThat(response.phone()).isEqualTo("3001234567");
    }

    @Test
    void passwordIsStoredOnlyAsBcryptHash() {
        serviceAt(NOW).register(validRequest());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        String hash = saved.getValue().getPasswordHash();
        assertThat(hash).isNotEqualTo("Secret_123").startsWith("$2a$");
        assertThat(passwordEncoder.matches("Secret_123", hash)).isTrue();
    }

    @Test
    void datesAreTruncatedToMicroseconds() {
        Instant withNanos = Instant.parse("2026-10-11T12:00:00.123456789Z");

        UserResponse response = serviceAt(withNanos).register(validRequest());

        // PostgreSQL guarda microsegundos: así el POST y un GET posterior devuelven lo mismo (design D3)
        assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-10-11T12:00:00.123456Z"));
        assertThat(response.termsAcceptedAt()).isEqualTo(response.createdAt());
    }

    /** Verifica el ErrorCode y que no se intentó guardar el usuario. */
    void assertRejected(Instant now, RegisterUserRequest request, ErrorCode expected) {
        assertThatThrownBy(() -> serviceAt(now).register(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(expected.getMessage())
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(expected);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Nested
    class Age {

        @Test
        void turning18TodayIsAllowed() {
            UserResponse response = serviceAt(NOW).register(request("ana@example.com", LocalDate.of(2008, 10, 11), true));

            assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        void turning18TomorrowIsUnderage() {
            assertRejected(NOW, request("ana@example.com", LocalDate.of(2008, 10, 12), true), ErrorCode.UNDERAGE_USER);
        }

        @Test
        void bornOnFebruary29IsStillUnderageOnFebruary28() {
            Instant february28 = Instant.parse("2026-02-28T12:00:00Z");

            assertRejected(february28, request("ana@example.com", LocalDate.of(2008, 2, 29), true), ErrorCode.UNDERAGE_USER);
        }

        @Test
        void ageIsCalculatedInUtc() {
            // 2026-10-10T23:30 en UTC: en Bogotá ya sería otra hora, pero en UTC aún no es el 11 → menor
            Instant lateOnTheTenth = Instant.parse("2026-10-10T23:30:00Z");

            assertRejected(lateOnTheTenth, request("ana@example.com", LocalDate.of(2008, 10, 11), true), ErrorCode.UNDERAGE_USER);
        }
    }

    @Nested
    class Terms {

        @Test
        void termsNotAcceptedIsRejected() {
            assertRejected(NOW, request("ana@example.com", LocalDate.of(1990, 5, 20), false), ErrorCode.TERMS_NOT_ACCEPTED);
        }
    }

    @Nested
    class DuplicateEmail {

        @Test
        void existingEmailIsRejected() {
            when(userRepository.existsByEmail("standard@shoplab.dev")).thenReturn(true);

            assertRejected(NOW, request("Standard@ShopLab.dev", LocalDate.of(1990, 5, 20), true), ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        @Test
        void concurrentRegistrationWithSameEmailIsAlso409() {
            // exists dice que no, pero otro registro guardó el mismo email antes del INSERT (§6.4)
            when(userRepository.existsByEmail("ana@example.com")).thenReturn(false);
            when(userRepository.saveAndFlush(any(User.class)))
                    .thenThrow(new DataIntegrityViolationException("uk_users_email"));

            assertThatThrownBy(() -> serviceAt(NOW).register(validRequest()))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
    }

    @Nested
    class RuleOrder {

        @Test
        void underageWinsOverTermsAndDuplicateEmail() {
            when(userRepository.existsByEmail("standard@shoplab.dev")).thenReturn(true);

            assertRejected(NOW, request("standard@shoplab.dev", LocalDate.of(2016, 1, 1), false), ErrorCode.UNDERAGE_USER);
        }

        @Test
        void termsWinOverDuplicateEmail() {
            when(userRepository.existsByEmail("standard@shoplab.dev")).thenReturn(true);

            assertRejected(NOW, request("standard@shoplab.dev", LocalDate.of(1990, 5, 20), false), ErrorCode.TERMS_NOT_ACCEPTED);
        }
    }

    @Nested
    class CurrentUser {

        @Test
        void existingUserIsReturned() {
            User user = UserMapperTest.ana();
            when(userRepository.findById(7L)).thenReturn(Optional.of(user));

            UserResponse response = serviceAt(NOW).findCurrentUser(7L);

            assertThat(response.id()).isEqualTo(7L);
            assertThat(response.email()).isEqualTo("ana@example.com");
        }

        @Test
        void missingUserMeansTheTokenIsInvalid() {
            // El token es válido, pero su usuario ya no existe (§7.6)
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> serviceAt(NOW).findCurrentUser(99L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("Authentication token is invalid")
                    .extracting(ex -> ((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.AUTH_TOKEN_INVALID);
        }
    }
}
