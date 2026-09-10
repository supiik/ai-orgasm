package com.orgasm.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgasm.dynamo.orgasm.NominateSongRequest;
import com.orgasm.dynamo.orgasm.NominationResponse;
import com.orgasm.dynamo.orgasm.OrgasmService;
import jakarta.validation.Validator;

public class NominateSongHandler extends BaseHandler<NominationResponse> {

    private final OrgasmService orgasmService;

    public NominateSongHandler() {
        var ctx = SpringContextHolder.get();
        this.orgasmService = ctx.getBean(OrgasmService.class);
    }

    NominateSongHandler(OrgasmService orgasmService, ObjectMapper mapper, Validator validator) {
        super(mapper, validator);
        this.orgasmService = orgasmService;
    }

    @Override
    protected NominationResponse execute(APIGatewayV2HTTPEvent event) throws Exception {
        String playlistId = pathSegment(event, 1);
        var request = parseBody(event, NominateSongRequest.class);
        return orgasmService.nominateSong(playlistId, request);
    }

    @Override
    protected int successStatus() {
        return 201;
    }
}
