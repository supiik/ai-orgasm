package com.orgasm.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgasm.dynamo.orgasm.OrgasmService;
import jakarta.validation.Validator;

import java.util.Map;

public class ListPlaylistsByContributorHandler extends BaseHandler<Map<String, Object>> {

    private final OrgasmService orgasmService;

    public ListPlaylistsByContributorHandler() {
        var ctx = SpringContextHolder.get();
        this.orgasmService = ctx.getBean(OrgasmService.class);
    }

    ListPlaylistsByContributorHandler(OrgasmService orgasmService, ObjectMapper mapper, Validator validator) {
        super(mapper, validator);
        this.orgasmService = orgasmService;
    }

    @Override
    protected Map<String, Object> execute(APIGatewayV2HTTPEvent event) {
        String contributorId = pathSegment(event, 1);
        return pageBody(orgasmService.findPlaylistsByContributor(contributorId, pageable(event)));
    }
}
