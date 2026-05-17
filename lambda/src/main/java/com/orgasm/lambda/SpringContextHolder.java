package com.orgasm.lambda;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ApplicationContext;

final class SpringContextHolder {

    private static final ApplicationContext CTX;

    static {
        SpringApplication app = new SpringApplication(LambdaApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        CTX = app.run();
    }

    private SpringContextHolder() {}

    static ApplicationContext get() {
        return CTX;
    }
}
