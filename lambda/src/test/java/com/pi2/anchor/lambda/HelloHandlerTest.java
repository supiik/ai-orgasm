package com.pi2.anchor.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pi2.anchor.backend.sample.SampleService;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;

import java.util.function.UnaryOperator;

import static com.pi2.anchor.lambda.LambdaTestSupport.VALIDATOR;
import static com.pi2.anchor.lambda.LambdaTestSupport.getEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class HelloHandlerTest {

    @Mock SampleService sampleService;
    private final Context context = mock(Context.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void returns200WithMessage() {
        when(sampleService.findAll(any(UnaryOperator.class), any())).thenReturn(Page.empty());
        var handler = new HelloHandler(sampleService, mapper, VALIDATOR);

        var response = handler.handleRequest(getEvent(), context);

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getBody()).contains("Hello from Lambda!");
    }

    @Test
    void returns503_whenCircuitOpen() {
        when(sampleService.findAll(any(UnaryOperator.class), any()))
                .thenThrow(mock(CallNotPermittedException.class));
        var handler = new HelloHandler(sampleService, mapper, VALIDATOR);

        var response = handler.handleRequest(getEvent(), context);

        assertThat(response.getStatusCode()).isEqualTo(503);
        assertThat(response.getBody()).contains("temporarily unavailable");
    }

    @Test
    void returns500_whenSerializationFails() throws Exception {
        when(sampleService.findAll(any(UnaryOperator.class), any())).thenReturn(Page.empty());
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any())).thenThrow(new RuntimeException("boom"));
        var handler = new HelloHandler(sampleService, failingMapper, VALIDATOR);

        var response = handler.handleRequest(getEvent(), context);

        assertThat(response.getStatusCode()).isEqualTo(500);
        assertThat(response.getBody()).contains("Serialization failed");
    }
}
