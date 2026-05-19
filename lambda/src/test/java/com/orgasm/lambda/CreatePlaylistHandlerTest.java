package com.orgasm.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgasm.backend.playlist.CreatePlaylistRequest;
import com.orgasm.backend.playlist.PlaylistResponse;
import com.orgasm.backend.playlist.PlaylistService;
import com.orgasm.backend.playlist.PlaylistStatus;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static com.orgasm.lambda.LambdaTestSupport.VALIDATOR;
import static com.orgasm.lambda.LambdaTestSupport.postEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreatePlaylistHandlerTest {

    @Mock PlaylistService playlistService;
    private final Context context = mock(Context.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    private CreatePlaylistHandler handler() {
        return new CreatePlaylistHandler(playlistService, mapper, VALIDATOR);
    }

    private static PlaylistResponse stubResponse() {
        return new PlaylistResponse("play-0001", "My Mix", "desc", PlaylistStatus.NEW, null, null, null, null, 0L, Instant.EPOCH, Instant.EPOCH);
    }

    // Explicit cast resolves the create(CreatePlaylistRequest) vs create(UnaryOperator) ambiguity
    private static CreatePlaylistRequest anyRequest() {
        return (CreatePlaylistRequest) any();
    }

    @Test
    void returns201_withCreatedPlaylist() {
        when(playlistService.create(anyRequest())).thenReturn(stubResponse());
        var event = postEvent("{\"name\":\"My Mix\",\"description\":\"desc\"}");

        var response = handler().handleRequest(event, context);

        assertThat(response.getStatusCode()).isEqualTo(201);
        assertThat(response.getBody()).contains("My Mix");
    }

    @Test
    void returns400_whenNameBlank() {
        var event = postEvent("{\"name\":\"\"}");

        var response = handler().handleRequest(event, context);

        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getBody()).contains("errors");
    }

    @Test
    void returns400_whenBodyMalformedJson() {
        var event = postEvent("not-json");

        var response = handler().handleRequest(event, context);

        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getBody()).contains("error");
    }

    @Test
    void returns404_whenServiceThrowsNotFound() {
        when(playlistService.create(anyRequest())).thenThrow(new EntityNotFoundException("Playlist not found: 99"));
        var event = postEvent("{\"name\":\"My Mix\"}");

        var response = handler().handleRequest(event, context);

        assertThat(response.getStatusCode()).isEqualTo(404);
        assertThat(response.getBody()).contains("Playlist not found: 99");
    }

    @Test
    void returns500_whenServiceThrowsUnexpected() {
        when(playlistService.create(anyRequest())).thenThrow(new RuntimeException("boom"));
        var event = postEvent("{\"name\":\"My Mix\"}");

        var response = handler().handleRequest(event, context);

        assertThat(response.getStatusCode()).isEqualTo(500);
        assertThat(response.getBody()).contains("Internal server error");
    }

    @SuppressWarnings("unchecked")
    @Test
    void returns500_whenSerializationFails() throws Exception {
        var failingMapper = mock(ObjectMapper.class);
        when(failingMapper.readValue(any(String.class), any(Class.class)))
                .thenReturn(new CreatePlaylistRequest("x", null, null));
        when(failingMapper.writeValueAsString(any())).thenThrow(new RuntimeException("ser-fail"));
        var handler = new CreatePlaylistHandler(playlistService, failingMapper, VALIDATOR);

        var response = handler.handleRequest(postEvent("{\"name\":\"x\"}"), context);

        assertThat(response.getStatusCode()).isEqualTo(500);
        assertThat(response.getBody()).contains("Serialization failed");
    }
}
