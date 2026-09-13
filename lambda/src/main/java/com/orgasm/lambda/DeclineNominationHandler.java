package com.orgasm.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgasm.dynamo.orgasm.NominationResponse;
import com.orgasm.dynamo.orgasm.OrgasmService;
import com.orgasm.dynamo.orgasm.ReviewNominationRequest;
import jakarta.validation.Validator;

public class DeclineNominationHandler extends BaseHandler<NominationResponse> {

    private final OrgasmService orgasmService;

    public DeclineNominationHandler() {
        var ctx = SpringContextHolder.get();
        this.orgasmService = ctx.getBean(OrgasmService.class);
    }

    DeclineNominationHandler(OrgasmService orgasmService, ObjectMapper mapper, Validator validator) {
        super(mapper, validator);
        this.orgasmService = orgasmService;
    }

    @Override
    protected NominationResponse execute(APIGatewayV2HTTPEvent event) throws Exception {
        String nominationId = pathSegment(event, 1);
        var request = parseBody(event, ReviewNominationRequest.class);
        return orgasmService.declineNomination(nominationId, request);
    }
}
