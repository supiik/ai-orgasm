package com.orgasm.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgasm.dynamo.contributor.ContributorService;
import jakarta.validation.Validator;

public class DeleteContributorHandler extends BaseHandler<Void> {

    private final ContributorService contributorService;

    public DeleteContributorHandler() {
        var ctx = SpringContextHolder.get();
        this.contributorService = ctx.getBean(ContributorService.class);
    }

    DeleteContributorHandler(ContributorService contributorService, ObjectMapper mapper, Validator validator) {
        super(mapper, validator);
        this.contributorService = contributorService;
    }

    @Override
    protected Void execute(APIGatewayV2HTTPEvent event) {
        contributorService.delete(pathSegment(event, 0));
        return null;
    }

    @Override
    protected int successStatus() {
        return 204;
    }
}
