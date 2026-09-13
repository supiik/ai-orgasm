package com.orgasm.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgasm.dynamo.playlist.PlaylistResponse;
import com.orgasm.dynamo.playlist.PlaylistService;
import com.orgasm.dynamo.playlist.UpdatePlaylistRequest;
import jakarta.validation.Validator;

public class UpdatePlaylistHandler extends BaseHandler<PlaylistResponse> {

    private final PlaylistService playlistService;

    public UpdatePlaylistHandler() {
        var ctx = SpringContextHolder.get();
        this.playlistService = ctx.getBean(PlaylistService.class);
    }

    UpdatePlaylistHandler(PlaylistService playlistService, ObjectMapper mapper, Validator validator) {
        super(mapper, validator);
        this.playlistService = playlistService;
    }

    @Override
    protected PlaylistResponse execute(APIGatewayV2HTTPEvent event) throws Exception {
        String id = pathSegment(event, 0);
        var request = parseBody(event, UpdatePlaylistRequest.class);
        return playlistService.update(id, request);
    }
}
