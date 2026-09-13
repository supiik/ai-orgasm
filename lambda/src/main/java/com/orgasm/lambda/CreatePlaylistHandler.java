package com.orgasm.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgasm.dynamo.playlist.CreatePlaylistRequest;
import com.orgasm.dynamo.playlist.PlaylistResponse;
import com.orgasm.dynamo.playlist.PlaylistService;
import jakarta.validation.Validator;

public class CreatePlaylistHandler extends BaseHandler<PlaylistResponse> {

    private final PlaylistService playlistService;

    public CreatePlaylistHandler() {
        var ctx = SpringContextHolder.get();
        this.playlistService = ctx.getBean(PlaylistService.class);
    }

    CreatePlaylistHandler(PlaylistService playlistService, ObjectMapper mapper, Validator validator) {
        super(mapper, validator);
        this.playlistService = playlistService;
    }

    @Override
    protected PlaylistResponse execute(APIGatewayV2HTTPEvent event) throws Exception {
        var request = parseBody(event, CreatePlaylistRequest.class);
        return playlistService.create(request);
    }

    @Override
    protected int successStatus() {
        return 201;
    }
}
