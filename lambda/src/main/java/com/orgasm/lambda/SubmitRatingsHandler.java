package com.orgasm.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgasm.dynamo.orgasm.OrgasmService;
import com.orgasm.dynamo.orgasm.SubmitRatingsRequest;
import jakarta.validation.Validator;

public class SubmitRatingsHandler extends BaseHandler<Void> {

    private final OrgasmService orgasmService;

    public SubmitRatingsHandler() {
        var ctx = SpringContextHolder.get();
        this.orgasmService = ctx.getBean(OrgasmService.class);
    }

    SubmitRatingsHandler(OrgasmService orgasmService, ObjectMapper mapper, Validator validator) {
        super(mapper, validator);
        this.orgasmService = orgasmService;
    }

    @Override
    protected Void execute(APIGatewayV2HTTPEvent event) throws Exception {
        String playlistId = pathSegment(event, 1);
        var request = parseBody(event, SubmitRatingsRequest.class);
        orgasmService.submitRatings(playlistId, request);
        return null;
    }

    @Override
    protected int successStatus() {
        return 204;
    }
}
