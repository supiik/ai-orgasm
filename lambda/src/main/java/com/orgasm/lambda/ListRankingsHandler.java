package com.orgasm.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgasm.dynamo.orgasm.OrgasmService;
import com.orgasm.dynamo.orgasm.RankingResponse;
import jakarta.validation.Validator;

import java.util.List;

public class ListRankingsHandler extends BaseHandler<List<RankingResponse>> {

    private final OrgasmService orgasmService;

    public ListRankingsHandler() {
        var ctx = SpringContextHolder.get();
        this.orgasmService = ctx.getBean(OrgasmService.class);
    }

    ListRankingsHandler(OrgasmService orgasmService, ObjectMapper mapper, Validator validator) {
        super(mapper, validator);
        this.orgasmService = orgasmService;
    }

    @Override
    protected List<RankingResponse> execute(APIGatewayV2HTTPEvent event) {
        return orgasmService.getRankings();
    }
}
