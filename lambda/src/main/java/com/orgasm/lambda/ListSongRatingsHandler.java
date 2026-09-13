package com.orgasm.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgasm.dynamo.orgasm.OrgasmService;
import com.orgasm.dynamo.orgasm.SongRatingResponse;
import jakarta.validation.Validator;

import java.util.List;

public class ListSongRatingsHandler extends BaseHandler<List<SongRatingResponse>> {

    private final OrgasmService orgasmService;

    public ListSongRatingsHandler() {
        var ctx = SpringContextHolder.get();
        this.orgasmService = ctx.getBean(OrgasmService.class);
    }

    ListSongRatingsHandler(OrgasmService orgasmService, ObjectMapper mapper, Validator validator) {
        super(mapper, validator);
        this.orgasmService = orgasmService;
    }

    @Override
    protected List<SongRatingResponse> execute(APIGatewayV2HTTPEvent event) {
        String playlistId = pathSegment(event, 1);
        return orgasmService.getSongRatings(playlistId);
    }
}
