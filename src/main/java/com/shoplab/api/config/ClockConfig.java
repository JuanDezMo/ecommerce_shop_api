package com.shoplab.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Reloj único de la aplicación (§6.5). Los services usan LocalDate.now(clock) e Instant.now(clock),
 * así los tests pueden fijar la fecha con Clock.fixed(...).
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
