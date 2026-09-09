package com.orgasm.dynamo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DynamoTestApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(DynamoTestApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.run(args);
    }
}
