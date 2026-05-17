package com.orgasm.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HelloHandlerTest {

    private final Context context = mock(Context.class);

    @Test
    void returns200WithMessage() {
        var handler = new HelloHandler();
        var event = new APIGatewayProxyRequestEvent().withPath("/hello");

        var response = handler.handleRequest(event, context);

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getBody()).contains("Hello from Lambda!");
    }

    @Test
    void returns500_whenSerializationFails() throws JsonProcessingException {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any()))
                .thenThrow(new RuntimeException("boom"));
        var handler = new HelloHandler(failingMapper);
        var event = new APIGatewayProxyRequestEvent().withPath("/hello");

        var response = handler.handleRequest(event, context);

        assertThat(response.getStatusCode()).isEqualTo(500);
        assertThat(response.getBody()).contains("Internal error");
    }
}
