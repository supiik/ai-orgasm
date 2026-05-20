package com.etactics.proxima.service.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.PathContainer;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        configurer
                .usePathSegment(1, path -> path.pathWithinApplication().elements().stream()
                        .filter(e -> e instanceof PathContainer.PathSegment)
                        .skip(1)
                        .findFirst()
                        .map(e -> e.value().matches("v\\d+.*"))
                        .orElse(false))
                .setVersionRequired(false);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
