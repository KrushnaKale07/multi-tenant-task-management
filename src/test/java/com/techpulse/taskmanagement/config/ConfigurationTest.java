package com.techpulse.taskmanagement.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationTest {

    @Test
    void openApiDeclaresBearerAuthentication() {
        OpenAPI openApi = new OpenApiConfig().openApi();

        assertThat(openApi.getInfo().getTitle()).isEqualTo("Multi-Tenant Task Management API");
        assertThat(openApi.getComponents().getSecuritySchemes()).containsKey("bearerAuth");
        assertThat(openApi.getComponents().getSecuritySchemes().get("bearerAuth").getBearerFormat()).isEqualTo("JWT");
        assertThat(openApi.getSecurity()).hasSize(1);
    }

    @Test
    void passwordEncoderHashesWithBcrypt() {
        PasswordEncoder encoder = new SecurityConfig().passwordEncoder();

        String hash = encoder.encode("Passw0rd!");

        assertThat(hash).startsWith("$2").isNotEqualTo("Passw0rd!");
        assertThat(encoder.matches("Passw0rd!", hash)).isTrue();
        assertThat(encoder.matches("other", hash)).isFalse();
    }

    @Test
    void jwtPropertiesHoldConfiguredValues() {
        JwtProperties properties = new JwtProperties("secret", 15);

        assertThat(properties.secret()).isEqualTo("secret");
        assertThat(properties.expirationMinutes()).isEqualTo(15);
    }
}
