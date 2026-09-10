package com.orgasm.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgasm.dynamo.orgasm.OrgasmService;
import com.orgasm.dynamo.orgasm.SubmitGuessesRequest;
import jakarta.validation.Validator;

public class SubmitGuessesHandler extends BaseHandler<Void> {

    private final OrgasmService orgasmService;

    public SubmitGuessesHandler() {
        var ctx = SpringContextHolder.get();
        this.orgasmService = ctx.getBean(OrgasmService.class);
    }

    SubmitGuessesHandler(OrgasmService orgasmService, ObjectMapper mapper, Validator validator) {
        super(mapper, validator);
        this.orgasmService = orgasmService;
    }

    @Override
    protected Void execute(APIGatewayV2HTTPEvent event) throws Exception {
        String playlistId = pathSegment(event, 1);
        var request = parseBody(event, SubmitGuessesRequest.class);
        orgasmService.submitGuesses(playlistId, request);
        return null;
    }

    @Override
    protected int successStatus() {
        return 204;
    }
}
