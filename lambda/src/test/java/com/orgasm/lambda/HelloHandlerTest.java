package com.orgasm.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.amazonaws.services.lambda.runtime.Context;

class HelloHandlerTest {

    private final HelloHandler handler = new HelloHandler();

    @Test
    void returns200WithMessage() {
        var event = new APIGatewayProxyRequestEvent().withPath("/hello");
        var context = mock(Context.class);

        var response = handler.handleRequest(event, context);

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getBody()).contains("Hello from Lambda!");
    }
}
