package com.orgasm.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgasm.dynamo.playlist.PlaylistResponse;
import com.orgasm.dynamo.playlist.PlaylistService;
import jakarta.validation.Validator;

import java.util.NoSuchElementException;

public class GetPlaylistHandler extends BaseHandler<PlaylistResponse> {

    private final PlaylistService playlistService;

    public GetPlaylistHandler() {
        var ctx = SpringContextHolder.get();
        this.playlistService = ctx.getBean(PlaylistService.class);
    }

    GetPlaylistHandler(PlaylistService playlistService, ObjectMapper mapper, Validator validator) {
        super(mapper, validator);
        this.playlistService = playlistService;
    }

    @Override
    protected PlaylistResponse execute(APIGatewayV2HTTPEvent event) {
        String id = pathSegment(event, 0);
        return playlistService.findById(id).orElseThrow(() -> new NoSuchElementException("Playlist not found: " + id));
    }
}
