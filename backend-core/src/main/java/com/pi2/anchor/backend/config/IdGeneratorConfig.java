package com.pi2.anchor.backend.config;

import com.pi2.anchor.backend.domain.IdGenerator;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IdGeneratorConfig {

    @Value("${app.id.secret:0000000000000000}")
    private String idSecret;

    @PostConstruct
    public void configure() {
        IdGenerator.configure(Long.parseUnsignedLong(idSecret, 16));
    }
}
