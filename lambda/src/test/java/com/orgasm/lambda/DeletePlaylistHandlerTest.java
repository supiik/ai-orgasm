package com.orgasm.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgasm.dynamo.playlist.PlaylistService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.orgasm.lambda.LambdaTestSupport.VALIDATOR;
import static com.orgasm.lambda.LambdaTestSupport.event;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeletePlaylistHandlerTest {

    @Mock PlaylistService playlistService;
    private final Context context = mock(Context.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void returns204_withEmptyBody() {
        var handler = new DeletePlaylistHandler(playlistService, mapper, VALIDATOR);

        var response = handler.handleRequest(event("DELETE", "/api/v1/playlists/play-1", null), context);

        assertThat(response.getStatusCode()).isEqualTo(204);
        assertThat(response.getBody()).isNull();
        verify(playlistService).delete("play-1");
    }
}
