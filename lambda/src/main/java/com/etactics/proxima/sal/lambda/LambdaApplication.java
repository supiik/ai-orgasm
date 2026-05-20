package com.etactics.proxima.sal.lambda;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.etactics.proxima.sal.backend")
public class LambdaApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(LambdaApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.run(args);
    }
}
