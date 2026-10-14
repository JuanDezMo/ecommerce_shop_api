package com.shoplab.api.config;

import com.shoplab.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * docker compose arranca con el perfil "testing" por defecto: la aplicación debe levantar con él.
 */
@ActiveProfiles("testing")
class TestingProfileIT extends IntegrationTest {

    @Autowired
    Environment environment;

    @Test
    void contextLoadsWithTestingProfile() {
        assertThat(environment.getActiveProfiles()).containsExactly("testing");
        assertThat(environment.getProperty("shop.profile-description")).isEqualTo("testing");
    }
}
