package com.orgasm.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgasm.dynamo.playlist.PlaylistResponse;
import com.orgasm.dynamo.playlist.PlaylistService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;
import java.util.function.UnaryOperator;

import static com.orgasm.lambda.LambdaTestSupport.VALIDATOR;
import static com.orgasm.lambda.LambdaTestSupport.event;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class ListPlaylistsHandlerTest {

    @Mock PlaylistService playlistService;
    private final Context context = mock(Context.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    private ListPlaylistsHandler handler() {
        return new ListPlaylistsHandler(playlistService, mapper, VALIDATOR);
    }

    @Test
    void buildsPageableFromQueryParams() {
        when(playlistService.findAll(any(UnaryOperator.class), any(Pageable.class))).thenReturn(Page.empty());

        var event = event("GET", "/api/v1/playlists", null, Map.of("page", "1", "size", "20"));
        handler().handleRequest(event, context);

        var pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(playlistService).findAll(any(UnaryOperator.class), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void defaultsToUnpaged_whenNoSizeParam() {
        when(playlistService.findAll(any(UnaryOperator.class), any(Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(
                        java.util.List.of(PlaylistResponse.builder().id("play-1").build())));

        var response = handler().handleRequest(event("GET", "/api/v1/playlists", null), context);

        assertThat(response.getStatusCode()).isEqualTo(200);
        var pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(playlistService).findAll(any(UnaryOperator.class), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().isUnpaged()).isTrue();
    }
}
