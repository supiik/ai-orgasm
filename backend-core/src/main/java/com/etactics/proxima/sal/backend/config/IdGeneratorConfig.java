package com.etactics.proxima.sal.backend.config;

import com.etactics.proxima.sal.backend.domain.IdGenerator;
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
