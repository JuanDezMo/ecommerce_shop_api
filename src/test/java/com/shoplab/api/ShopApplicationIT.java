package com.shoplab.api;

import com.shoplab.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.assertj.core.api.Assertions.assertThat;

class ShopApplicationIT extends IntegrationTest {

    @Autowired
    ApplicationContext context;

    @Test
    void contextLoadsAgainstPostgres() {
        assertThat(context.getBean(ShopApplication.class)).isNotNull();
    }

    @Test
    void noInMemoryUserIsCreated() {
        // La API se autentica con JWT; Spring no debe crear un usuario "user" con contraseña aleatoria
        assertThat(context.getBeansOfType(UserDetailsService.class)).isEmpty();
    }
}
