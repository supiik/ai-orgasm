package com.orgasm.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgasm.dynamo.contributor.ContributorResponse;
import com.orgasm.dynamo.contributor.ContributorService;
import com.orgasm.dynamo.contributor.UpdateContributorRequest;
import jakarta.validation.Validator;

public class UpdateContributorHandler extends BaseHandler<ContributorResponse> {

    private final ContributorService contributorService;

    public UpdateContributorHandler() {
        var ctx = SpringContextHolder.get();
        this.contributorService = ctx.getBean(ContributorService.class);
    }

    UpdateContributorHandler(ContributorService contributorService, ObjectMapper mapper, Validator validator) {
        super(mapper, validator);
        this.contributorService = contributorService;
    }

    @Override
    protected ContributorResponse execute(APIGatewayV2HTTPEvent event) throws Exception {
        String id = pathSegment(event, 0);
        var request = parseBody(event, UpdateContributorRequest.class);
        return contributorService.update(id, request);
    }
}
