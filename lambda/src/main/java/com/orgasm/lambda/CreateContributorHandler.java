package com.orgasm.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgasm.dynamo.contributor.ContributorResponse;
import com.orgasm.dynamo.contributor.ContributorService;
import com.orgasm.dynamo.contributor.CreateContributorRequest;
import jakarta.validation.Validator;

public class CreateContributorHandler extends BaseHandler<ContributorResponse> {

    private final ContributorService contributorService;

    public CreateContributorHandler() {
        var ctx = SpringContextHolder.get();
        this.contributorService = ctx.getBean(ContributorService.class);
    }

    CreateContributorHandler(ContributorService contributorService, ObjectMapper mapper, Validator validator) {
        super(mapper, validator);
        this.contributorService = contributorService;
    }

    @Override
    protected ContributorResponse execute(APIGatewayV2HTTPEvent event) throws Exception {
        var request = parseBody(event, CreateContributorRequest.class);
        return contributorService.create(request);
    }

    @Override
    protected int successStatus() {
        return 201;
    }
}
