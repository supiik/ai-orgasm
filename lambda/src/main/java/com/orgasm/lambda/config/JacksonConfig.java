package com.orgasm.lambda.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Explicit Jackson 2.x {@link ObjectMapper} bean. Spring Boot 4 split Jackson auto-configuration
 * out into its own {@code spring-boot-jackson} module, which configures a Jackson 3.x
 * ({@code tools.jackson.databind.ObjectMapper}) bean — a different type from the Jackson 2.x
 * ({@code com.fasterxml.jackson.databind}) type every handler in this module depends on. Rather
 * than depend on Spring Boot's Jackson-generation auto-config (liable to shift again), define
 * the bean this app actually needs directly — same construction every test file already uses.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
