package com.orgasm.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgasm.dynamo.contributor.ContributorResponse;
import com.orgasm.dynamo.registration.LinkContributorRequest;
import com.orgasm.dynamo.registration.LinkContributorService;
import com.orgasm.lambda.auth.AuthMode;
import jakarta.validation.Validator;

/**
 * Called right after a Cognito sign-up, with a valid token but (possibly) no Contributor linked
 * yet — hence {@link AuthMode#AUTHENTICATED} rather than the default
 * {@link AuthMode#AUTHENTICATED_WITH_CONTRIBUTOR}. {@code cognitoSub}/{@code cognitoEmail} are
 * populated by {@code BaseHandler} from the validated token before {@code execute} runs.
 */
public class LinkContributorHandler extends BaseHandler<ContributorResponse> {

    private final LinkContributorService linkContributorService;

    public LinkContributorHandler() {
        var ctx = SpringContextHolder.get();
        this.linkContributorService = ctx.getBean(LinkContributorService.class);
    }

    LinkContributorHandler(LinkContributorService linkContributorService, ObjectMapper mapper, Validator validator) {
        super(mapper, validator);
        this.linkContributorService = linkContributorService;
    }

    @Override
    protected ContributorResponse execute(APIGatewayV2HTTPEvent event) throws Exception {
        var request = parseBody(event, LinkContributorRequest.class);
        return linkContributorService.link(request, cognitoSub, cognitoEmail);
    }

    @Override
    protected AuthMode authMode() {
        return AuthMode.AUTHENTICATED;
    }
}
