package com.orgasm.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgasm.dynamo.playlist.PlaylistResponse;
import com.orgasm.dynamo.playlist.PlaylistService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.orgasm.lambda.LambdaTestSupport.VALIDATOR;
import static com.orgasm.lambda.LambdaTestSupport.event;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetPlaylistHandlerTest {

    @Mock PlaylistService playlistService;
    private final Context context = mock(Context.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    private GetPlaylistHandler handler() {
        return new GetPlaylistHandler(playlistService, mapper, VALIDATOR);
    }

    @Test
    void returns200_withPlaylist_extractingIdFromLastPathSegment() {
        when(playlistService.findById("play-1")).thenReturn(Optional.of(PlaylistResponse.builder().id("play-1").name("Mix").build()));

        var response = handler().handleRequest(event("GET", "/api/v1/playlists/play-1", null), context);

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getBody()).contains("Mix");
    }

    @Test
    void returns404_whenMissing() {
        when(playlistService.findById("play-99")).thenReturn(Optional.empty());

        var response = handler().handleRequest(event("GET", "/api/v1/playlists/play-99", null), context);

        assertThat(response.getStatusCode()).isEqualTo(404);
    }
}
