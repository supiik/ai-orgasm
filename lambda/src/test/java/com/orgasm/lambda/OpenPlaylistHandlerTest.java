package com.orgasm.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgasm.dynamo.orgasm.OpenPlaylistRequest;
import com.orgasm.dynamo.orgasm.OrgasmService;
import com.orgasm.dynamo.playlist.PlaylistResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.orgasm.lambda.LambdaTestSupport.VALIDATOR;
import static com.orgasm.lambda.LambdaTestSupport.event;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenPlaylistHandlerTest {

    @Mock OrgasmService orgasmService;
    private final Context context = mock(Context.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    private OpenPlaylistHandler handler() {
        return new OpenPlaylistHandler(orgasmService, mapper, VALIDATOR);
    }

    private static String body() {
        return "{\"contributorId\":\"cont-1\",\"deadline\":\"2030-01-01T00:00:00Z\"}";
    }

    @Test
    void returns200_extractingPlaylistIdFromSecondToLastSegment() {
        when(orgasmService.openPlaylist(eq("play-1"), any(OpenPlaylistRequest.class)))
                .thenReturn(PlaylistResponse.builder().id("play-1").build());

        var response = handler().handleRequest(
                event("POST", "/api/v1/playlists/play-1/open", body()), context);

        assertThat(response.getStatusCode()).isEqualTo(200);
    }

    @Test
    void returns409_whenPlaylistNotNew() {
        when(orgasmService.openPlaylist(eq("play-1"), any(OpenPlaylistRequest.class)))
                .thenThrow(new IllegalStateException("Playlist must be NEW to open"));

        var response = handler().handleRequest(
                event("POST", "/api/v1/playlists/play-1/open", body()), context);

        assertThat(response.getStatusCode()).isEqualTo(409);
        assertThat(response.getBody()).contains("must be NEW");
    }
}
