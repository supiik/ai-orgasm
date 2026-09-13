package com.orgasm.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgasm.dynamo.playlist.PlaylistService;
import jakarta.validation.Validator;

public class DeletePlaylistHandler extends BaseHandler<Void> {

    private final PlaylistService playlistService;

    public DeletePlaylistHandler() {
        var ctx = SpringContextHolder.get();
        this.playlistService = ctx.getBean(PlaylistService.class);
    }

    DeletePlaylistHandler(PlaylistService playlistService, ObjectMapper mapper, Validator validator) {
        super(mapper, validator);
        this.playlistService = playlistService;
    }

    @Override
    protected Void execute(APIGatewayV2HTTPEvent event) {
        playlistService.delete(pathSegment(event, 0));
        return null;
    }

    @Override
    protected int successStatus() {
        return 204;
    }
}
