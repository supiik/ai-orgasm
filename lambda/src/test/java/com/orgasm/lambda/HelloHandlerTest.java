package com.orgasm.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgasm.backend.playlist.PlaylistService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HelloHandlerTest {

    @Mock PlaylistService playlistService;
    private final Context context = mock(Context.class);

    @Test
    void returns200WithMessage() {
        when(playlistService.findAll(any())).thenReturn(Page.empty());
        var handler = new HelloHandler(playlistService, new ObjectMapper().findAndRegisterModules());
        var event = new APIGatewayProxyRequestEvent().withPath("/hello");

        var response = handler.handleRequest(event, context);

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getBody()).contains("Hello from Lambda!");
    }

    @Test
    void returns500_whenSerializationFails() throws Exception {
        when(playlistService.findAll(any())).thenReturn(Page.empty());
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any()))
                .thenThrow(new RuntimeException("boom"));
        var handler = new HelloHandler(playlistService, failingMapper);
        var event = new APIGatewayProxyRequestEvent().withPath("/hello");

        var response = handler.handleRequest(event, context);

        assertThat(response.getStatusCode()).isEqualTo(500);
        assertThat(response.getBody()).contains("Internal error");
    }
}
