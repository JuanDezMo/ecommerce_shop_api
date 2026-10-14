package com.shoplab.api.auth;

import com.shoplab.api.auth.dto.LoginRequest;
import com.shoplab.api.auth.dto.LoginResponse;
import com.shoplab.api.common.exception.BusinessException;
import com.shoplab.api.common.exception.ErrorCode;
import com.shoplab.api.security.JwtProperties;
import com.shoplab.api.security.JwtService;
import com.shoplab.api.user.User;
import com.shoplab.api.user.UserRepository;
import com.shoplab.api.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reglas del login (§7.2 y §8.3) con repository y JwtService mockeados y BCrypt real (design D2).
 */
class AuthServiceImplTest {

    static final String PASSWORD = "Secret_123";
    // Se calcula una sola vez: BCrypt es lento a propósito
    static final String HASH = new BCryptPasswordEncoder().encode(PASSWORD);

    UserRepository userRepository;
    JwtService jwtService;
    PasswordEncoder passwordEncoder;
    AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        jwtService = mock(JwtService.class);
        // spy: BCrypt real, pero se puede verificar si recibió la contraseña
        passwordEncoder = spy(new BCryptPasswordEncoder());
        JwtProperties jwtProperties = new JwtProperties("0123456789abcdef0123456789abcdef", Duration.ofHours(1));
        authService = new AuthServiceImpl(userRepository, passwordEncoder, jwtService, jwtProperties);
    }

    /** User es una entidad con constructor protected (solo el paquete user la crea): en este test se mockea. */
    static User user(long id, String email, UserStatus status) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getEmail()).thenReturn(email);
        when(user.getPasswordHash()).thenReturn(HASH);
        when(user.getStatus()).thenReturn(status);
        return user;
    }

    void givenUser(User user) {
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    @Test
    void validCredentialsReturnABearerToken() {
        givenUser(user(1L, "standard@shoplab.dev", UserStatus.ACTIVE));
        when(jwtService.generateToken(1L, "standard@shoplab.dev")).thenReturn("header.payload.signature");

        LoginResponse response = authService.login(new LoginRequest("standard@shoplab.dev", PASSWORD));

        assertThat(response.accessToken()).isEqualTo("header.payload.signature");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600);
        verify(jwtService).generateToken(1L, "standard@shoplab.dev");
    }

    /** Verifica el ErrorCode y que no se generó ningún token. */
    void assertRejected(LoginRequest request, ErrorCode expected) {
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(expected.getMessage())
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(expected);
        verify(jwtService, never()).generateToken(anyLong(), anyString());
    }

    @Test
    void unknownEmailIsInvalidCredentials() {
        when(userRepository.findByEmail("nobody@shoplab.dev")).thenReturn(Optional.empty());

        assertRejected(new LoginRequest("nobody@shoplab.dev", PASSWORD), ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void wrongPasswordIsInvalidCredentials() {
        givenUser(user(1L, "standard@shoplab.dev", UserStatus.ACTIVE));

        assertRejected(new LoginRequest("standard@shoplab.dev", "Wrong_123"), ErrorCode.INVALID_CREDENTIALS);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Secret 123",   // espacio
            "Señal_123",    // no ASCII
            "Secret_123Secret_1234"})   // 21 caracteres
    void passwordOutsideThePolicyNeverReachesBcrypt(String password) {
        givenUser(user(1L, "standard@shoplab.dev", UserStatus.ACTIVE));

        assertRejected(new LoginRequest("standard@shoplab.dev", password), ErrorCode.INVALID_CREDENTIALS);
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void hundredCharacterPasswordIsInvalidCredentialsNot500() {
        givenUser(user(1L, "standard@shoplab.dev", UserStatus.ACTIVE));

        // Con más de 72 bytes BCrypt lanzaría una excepción (→ 500); el filtro de formato lo evita
        assertRejected(new LoginRequest("standard@shoplab.dev", "A".repeat(100)), ErrorCode.INVALID_CREDENTIALS);
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void lockedUserWithCorrectPasswordIsLocked() {
        givenUser(user(2L, "locked@shoplab.dev", UserStatus.LOCKED));

        assertRejected(new LoginRequest("locked@shoplab.dev", PASSWORD), ErrorCode.USER_LOCKED);
    }

    @Test
    void lockedUserWithWrongPasswordIsInvalidCredentials() {
        // Sin la contraseña correcta no se revela que la cuenta está bloqueada (§8.3)
        givenUser(user(2L, "locked@shoplab.dev", UserStatus.LOCKED));

        assertRejected(new LoginRequest("locked@shoplab.dev", "Wrong_123"), ErrorCode.INVALID_CREDENTIALS);
    }
}
