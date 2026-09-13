package com.orgasm.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgasm.dynamo.organization.OrganizationResponse;
import com.orgasm.dynamo.organization.OrganizationService;
import com.orgasm.lambda.auth.AuthMode;
import jakarta.validation.Validator;

import java.util.List;

public class ListOrganizationsHandler extends BaseHandler<List<OrganizationResponse>> {

    private final OrganizationService organizationService;

    public ListOrganizationsHandler() {
        var ctx = SpringContextHolder.get();
        this.organizationService = ctx.getBean(OrganizationService.class);
    }

    ListOrganizationsHandler(OrganizationService organizationService, ObjectMapper mapper, Validator validator) {
        super(mapper, validator);
        this.organizationService = organizationService;
    }

    @Override
    protected List<OrganizationResponse> execute(APIGatewayV2HTTPEvent event) {
        return organizationService.findAll();
    }

    @Override
    protected AuthMode authMode() {
        return AuthMode.PUBLIC;
    }
}
