package com.orgasm.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgasm.dynamo.orgasm.OrgasmService;
import jakarta.validation.Validator;

import java.util.Map;

public class ListNominationsHandler extends BaseHandler<Map<String, Object>> {

    private final OrgasmService orgasmService;

    public ListNominationsHandler() {
        var ctx = SpringContextHolder.get();
        this.orgasmService = ctx.getBean(OrgasmService.class);
    }

    ListNominationsHandler(OrgasmService orgasmService, ObjectMapper mapper, Validator validator) {
        super(mapper, validator);
        this.orgasmService = orgasmService;
    }

    @Override
    protected Map<String, Object> execute(APIGatewayV2HTTPEvent event) {
        String playlistId = pathSegment(event, 1);
        return pageBody(orgasmService.findNominations(playlistId, pageable(event)));
    }
}
