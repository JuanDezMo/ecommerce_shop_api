package com.shoplab.api.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fail fast del secreto JWT (§8.4, design D3). No necesita Docker.
 */
class JwtPropertiesTest {

    static final String SECRET_32_BYTES = "0123456789abcdef0123456789abcdef";
    static final String SECRET_31_BYTES = SECRET_32_BYTES.substring(1);

    @Configuration
    @EnableConfigurationProperties(JwtProperties.class)
    static class JwtConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(JwtConfig.class);

    @Test
    void missingSecretIsRejected() {
        assertThatThrownBy(() -> new JwtProperties(null, Duration.ofHours(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt.secret (JWT_SECRET) is required");
    }

    @Test
    void blankSecretIsRejected() {
        assertThatThrownBy(() -> new JwtProperties("   ", Duration.ofHours(1)))
                .hasMessageContaining("jwt.secret (JWT_SECRET) is required");
    }

    @Test
    void secretShorterThan32BytesIsRejected() {
        assertThatThrownBy(() -> new JwtProperties(SECRET_31_BYTES, Duration.ofHours(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt.secret must be at least 32 bytes");
    }

    @Test
    void secretLengthIsMeasuredInBytes() {
        // 16 "ñ" son 16 caracteres pero 32 bytes en UTF-8: se acepta
        assertThat(new JwtProperties("ñ".repeat(16), null).secret()).hasSize(16);
    }

    @Test
    void ttlDefaultsToOneHour() {
        assertThat(new JwtProperties(SECRET_32_BYTES, null).ttl()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void contextFailsToStartWithoutSecret() {
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("jwt.secret (JWT_SECRET) is required");
        });
    }

    @Test
    void contextStartsWithA32ByteSecretAndBindsTtl() {
        runner.withPropertyValues("jwt.secret=" + SECRET_32_BYTES, "jwt.ttl=15m")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(JwtProperties.class).ttl()).isEqualTo(Duration.ofMinutes(15));
                });
    }
}
