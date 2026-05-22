package com.pi2.anchor.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BackendCoreTestApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(BackendCoreTestApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.run(args);
    }
}
