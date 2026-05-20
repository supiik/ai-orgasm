package com.etactics.proxima.sal.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

final class LambdaTestSupport {

    static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    static APIGatewayV2HTTPEvent postEvent(String body) {
        return event("POST", "/", body);
    }

    static APIGatewayV2HTTPEvent getEvent() {
        return event("GET", "/", null);
    }

    private static APIGatewayV2HTTPEvent event(String method, String rawPath, String body) {
        return APIGatewayV2HTTPEvent.builder()
                .withRequestContext(APIGatewayV2HTTPEvent.RequestContext.builder()
                        .withHttp(APIGatewayV2HTTPEvent.RequestContext.Http.builder()
                                .withMethod(method)
                                .build())
                        .build())
                .withRawPath(rawPath)
                .withBody(body)
                .build();
    }

    private LambdaTestSupport() {}
}
