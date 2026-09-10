package com.orgasm.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgasm.dynamo.contributor.ContributorResponse;
import com.orgasm.dynamo.registration.RegisterContributorRequest;
import com.orgasm.dynamo.registration.RegistrationService;
import com.orgasm.lambda.auth.AuthMode;
import jakarta.validation.Validator;

public class RegisterContributorHandler extends BaseHandler<ContributorResponse> {

    private final RegistrationService registrationService;

    public RegisterContributorHandler() {
        var ctx = SpringContextHolder.get();
        this.registrationService = ctx.getBean(RegistrationService.class);
    }

    RegisterContributorHandler(RegistrationService registrationService, ObjectMapper mapper, Validator validator) {
        super(mapper, validator);
        this.registrationService = registrationService;
    }

    @Override
    protected ContributorResponse execute(APIGatewayV2HTTPEvent event) throws Exception {
        var request = parseBody(event, RegisterContributorRequest.class);
        return registrationService.register(request);
    }

    @Override
    protected int successStatus() {
        return 201;
    }

    @Override
    protected AuthMode authMode() {
        return AuthMode.PUBLIC;
    }
}
