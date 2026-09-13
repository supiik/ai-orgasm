package com.orgasm.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgasm.dynamo.orgasm.GuessResponse;
import com.orgasm.dynamo.orgasm.OrgasmService;
import jakarta.validation.Validator;

import java.util.List;

public class ListGuessesHandler extends BaseHandler<List<GuessResponse>> {

    private final OrgasmService orgasmService;

    public ListGuessesHandler() {
        var ctx = SpringContextHolder.get();
        this.orgasmService = ctx.getBean(OrgasmService.class);
    }

    ListGuessesHandler(OrgasmService orgasmService, ObjectMapper mapper, Validator validator) {
        super(mapper, validator);
        this.orgasmService = orgasmService;
    }

    @Override
    protected List<GuessResponse> execute(APIGatewayV2HTTPEvent event) {
        String playlistId = pathSegment(event, 1);
        return orgasmService.getGuesses(playlistId);
    }
}
