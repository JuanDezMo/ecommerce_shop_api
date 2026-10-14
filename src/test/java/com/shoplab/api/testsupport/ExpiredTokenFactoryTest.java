package com.shoplab.api.testsupport;

import com.shoplab.api.common.exception.BusinessException;
import com.shoplab.api.common.exception.ErrorCode;
import com.shoplab.api.security.InvalidTokenException;
import com.shoplab.api.security.JjwtService;
import com.shoplab.api.security.JwtProperties;
import com.shoplab.api.support.TestJwt;
import com.shoplab.api.testsupport.dto.ExpiredTokenResponse;
import com.shoplab.api.user.User;
import com.shoplab.api.user.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Token ya expirado, firmado con el mismo código que el login (design D3).
 */
class ExpiredTokenFactoryTest {

    final JwtProperties properties = new JwtProperties(TestJwt.SECRET, Duration.ofMinutes(15));
    UserRepository userRepository;
    ExpiredTokenFactory factory;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        factory = new ExpiredTokenFactory(userRepository, properties, Clock.systemUTC());
    }

    void givenUser(long id, String email) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getEmail()).thenReturn(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
    }

    @Test
    void tokenIsRejectedAsExpiredByTheRealValidation() {
        givenUser(1L, "standard@shoplab.dev");

        ExpiredTokenResponse response = factory.create("standard@shoplab.dev");

        assertThat(response.tokenType()).isEqualTo("Bearer");
        JjwtService apiValidation = new JjwtService(properties, Clock.systemUTC());
        assertThatThrownBy(() -> apiValidation.parse(response.accessToken()))
                .isInstanceOf(InvalidTokenException.class)
                .extracting(ex -> ((InvalidTokenException) ex).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_TOKEN_EXPIRED);
    }

    @Test
    void tokenBelongsToTheUserAndExpiredOneMinuteAgo() {
        givenUser(1L, "standard@shoplab.dev");

        String token = factory.create("standard@shoplab.dev").accessToken();

        // Se lee aceptando la expiración (tolerancia de 1 h) solo para inspeccionar los claims
        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(TestJwt.SECRET.getBytes(StandardCharsets.UTF_8)))
                .clockSkewSeconds(3600)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.get("email", String.class)).isEqualTo("standard@shoplab.dev");
        long secondsSinceExpiration = (System.currentTimeMillis() - claims.getExpiration().getTime()) / 1000;
        assertThat(secondsSinceExpiration).isBetween(55L, 70L);
    }

    @Test
    void unknownEmailIsUserNotFound() {
        when(userRepository.findByEmail("nobody@shoplab.dev")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> factory.create("nobody@shoplab.dev"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("User not found")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
}
