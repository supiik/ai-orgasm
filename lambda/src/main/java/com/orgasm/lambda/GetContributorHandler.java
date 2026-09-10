package com.orgasm.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgasm.dynamo.contributor.ContributorResponse;
import com.orgasm.dynamo.contributor.ContributorService;
import jakarta.validation.Validator;

import java.util.NoSuchElementException;

public class GetContributorHandler extends BaseHandler<ContributorResponse> {

    private final ContributorService contributorService;

    public GetContributorHandler() {
        var ctx = SpringContextHolder.get();
        this.contributorService = ctx.getBean(ContributorService.class);
    }

    GetContributorHandler(ContributorService contributorService, ObjectMapper mapper, Validator validator) {
        super(mapper, validator);
        this.contributorService = contributorService;
    }

    @Override
    protected ContributorResponse execute(APIGatewayV2HTTPEvent event) {
        String id = pathSegment(event, 0);
        return contributorService.findById(id).orElseThrow(() -> new NoSuchElementException("Contributor not found: " + id));
    }
}
