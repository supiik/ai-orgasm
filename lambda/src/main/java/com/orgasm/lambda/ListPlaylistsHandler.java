package com.orgasm.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgasm.dynamo.playlist.PlaylistService;
import jakarta.validation.Validator;

import java.util.Map;

public class ListPlaylistsHandler extends BaseHandler<Map<String, Object>> {

    private final PlaylistService playlistService;

    public ListPlaylistsHandler() {
        var ctx = SpringContextHolder.get();
        this.playlistService = ctx.getBean(PlaylistService.class);
    }

    ListPlaylistsHandler(PlaylistService playlistService, ObjectMapper mapper, Validator validator) {
        super(mapper, validator);
        this.playlistService = playlistService;
    }

    @Override
    protected Map<String, Object> execute(APIGatewayV2HTTPEvent event) {
        String name = queryParam(event, "name");
        return pageBody(playlistService.findAll(b -> b.name(name), pageable(event)));
    }
}
