package com.gymtracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI gymTrackerOpenAPI() {
        final String bearerScheme = "bearer-jwt";
        return new OpenAPI()
                .info(new Info()
                        .title("Gym Tracker API")
                        .description("REST API for gym attendance. Use **Authorize** with a JWT from `POST /api/auth/login`.")
                        .version("1.0"))
                .components(new Components().addSecuritySchemes(bearerScheme,
                        new SecurityScheme()
                                .name(bearerScheme)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
