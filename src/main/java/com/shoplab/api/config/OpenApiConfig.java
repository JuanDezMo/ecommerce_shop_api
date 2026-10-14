package com.shoplab.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentación OpenAPI (Swagger UI en /swagger-ui.html).
 * Declara el esquema "bearerAuth" para poder pegar el JWT en el botón "Authorize".
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI shopOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ShopLab API")
                        .version("v1")
                        .description("Backend de práctica para automatización de pruebas móviles y de API"))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
