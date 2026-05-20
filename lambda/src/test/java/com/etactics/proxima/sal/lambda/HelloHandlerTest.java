package com.etactics.proxima.sal.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.etactics.proxima.sal.backend.playlist.PlaylistService;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;

import static com.etactics.proxima.sal.lambda.LambdaTestSupport.VALIDATOR;
import static com.etactics.proxima.sal.lambda.LambdaTestSupport.getEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class HelloHandlerTest {

    @Mock PlaylistService playlistService;
    private final Context context = mock(Context.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void returns200WithMessage() {
        when(playlistService.findAll(any(UnaryOperator.class), any())).thenReturn(Page.empty());
        var handler = new HelloHandler(playlistService, mapper, VALIDATOR);

        var response = handler.handleRequest(getEvent(), context);

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getBody()).contains("Hello from Lambda!");
    }

    @Test
    void returns503_whenCircuitOpen() {
        when(playlistService.findAll(any(UnaryOperator.class), any()))
                .thenThrow(mock(CallNotPermittedException.class));
        var handler = new HelloHandler(playlistService, mapper, VALIDATOR);

        var response = handler.handleRequest(getEvent(), context);

        assertThat(response.getStatusCode()).isEqualTo(503);
        assertThat(response.getBody()).contains("temporarily unavailable");
    }

    @Test
    void returns500_whenSerializationFails() throws Exception {
        when(playlistService.findAll(any(UnaryOperator.class), any())).thenReturn(Page.empty());
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any())).thenThrow(new RuntimeException("boom"));
        var handler = new HelloHandler(playlistService, failingMapper, VALIDATOR);

        var response = handler.handleRequest(getEvent(), context);

        assertThat(response.getStatusCode()).isEqualTo(500);
        assertThat(response.getBody()).contains("Serialization failed");
    }
}
