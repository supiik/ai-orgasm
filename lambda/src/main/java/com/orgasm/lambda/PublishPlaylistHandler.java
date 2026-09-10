package com.orgasm.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgasm.dynamo.orgasm.OrgasmService;
import com.orgasm.dynamo.orgasm.PublishPlaylistRequest;
import com.orgasm.dynamo.playlist.PlaylistResponse;
import jakarta.validation.Validator;

public class PublishPlaylistHandler extends BaseHandler<PlaylistResponse> {

    private final OrgasmService orgasmService;

    public PublishPlaylistHandler() {
        var ctx = SpringContextHolder.get();
        this.orgasmService = ctx.getBean(OrgasmService.class);
    }

    PublishPlaylistHandler(OrgasmService orgasmService, ObjectMapper mapper, Validator validator) {
        super(mapper, validator);
        this.orgasmService = orgasmService;
    }

    @Override
    protected PlaylistResponse execute(APIGatewayV2HTTPEvent event) throws Exception {
        String playlistId = pathSegment(event, 1);
        var request = parseBody(event, PublishPlaylistRequest.class);
        return orgasmService.publishPlaylist(playlistId, request);
    }
}
