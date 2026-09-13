package com.orgasm.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import java.util.Map;

final class LambdaTestSupport {

    static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    static APIGatewayV2HTTPEvent postEvent(String body) {
        return event("POST", "/", body);
    }

    static APIGatewayV2HTTPEvent getEvent() {
        return event("GET", "/", null);
    }

    static APIGatewayV2HTTPEvent event(String method, String rawPath, String body) {
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

    static APIGatewayV2HTTPEvent event(String method, String rawPath, String body, Map<String, String> queryParams) {
        return APIGatewayV2HTTPEvent.builder()
                .withRequestContext(APIGatewayV2HTTPEvent.RequestContext.builder()
                        .withHttp(APIGatewayV2HTTPEvent.RequestContext.Http.builder()
                                .withMethod(method)
                                .build())
                        .build())
                .withRawPath(rawPath)
                .withBody(body)
                .withQueryStringParameters(queryParams)
                .build();
    }

    static APIGatewayV2HTTPEvent authenticatedEvent(String method, String rawPath, String body, String bearerToken) {
        return APIGatewayV2HTTPEvent.builder()
                .withRequestContext(APIGatewayV2HTTPEvent.RequestContext.builder()
                        .withHttp(APIGatewayV2HTTPEvent.RequestContext.Http.builder()
                                .withMethod(method)
                                .build())
                        .build())
                .withRawPath(rawPath)
                .withBody(body)
                .withHeaders(Map.of("Authorization", "Bearer " + bearerToken))
                .build();
    }

    private LambdaTestSupport() {}
}
