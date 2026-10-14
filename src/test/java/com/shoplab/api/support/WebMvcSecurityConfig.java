package com.shoplab.api.support;

import com.shoplab.api.common.exception.GlobalExceptionHandler;
import com.shoplab.api.security.JjwtService;
import com.shoplab.api.security.JsonAccessDeniedHandler;
import com.shoplab.api.security.JwtAuthenticationEntryPoint;
import com.shoplab.api.security.JwtProperties;
import com.shoplab.api.security.JwtService;
import com.shoplab.api.security.SecurityConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.time.Clock;
import java.time.Duration;

/**
 * Seguridad REAL para los @WebMvcTest (design D7 de auth-jwt-filter): mismas reglas, filtro y respuestas
 * JSON que en producción, con un JjwtService de verdad que acepta los tokens de TestTokens.
 * Uso: @WebMvcTest(XController.class) + @Import(WebMvcSecurityConfig.class)
 */
@TestConfiguration
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JsonAccessDeniedHandler.class,
        GlobalExceptionHandler.class})
public class WebMvcSecurityConfig {

    @Bean
    JwtService jwtService() {
        return new JjwtService(new JwtProperties(TestJwt.SECRET, Duration.ofHours(1)), Clock.systemUTC());
    }
}
