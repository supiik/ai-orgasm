package com.orgasm.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgasm.dynamo.contributor.ContributorService;
import jakarta.validation.Validator;

import java.util.Map;

public class ListContributorsHandler extends BaseHandler<Map<String, Object>> {

    private final ContributorService contributorService;

    public ListContributorsHandler() {
        var ctx = SpringContextHolder.get();
        this.contributorService = ctx.getBean(ContributorService.class);
    }

    ListContributorsHandler(ContributorService contributorService, ObjectMapper mapper, Validator validator) {
        super(mapper, validator);
        this.contributorService = contributorService;
    }

    @Override
    protected Map<String, Object> execute(APIGatewayV2HTTPEvent event) {
        String name = queryParam(event, "name");
        return pageBody(contributorService.findAll(b -> b.name(name), pageable(event)));
    }
}
