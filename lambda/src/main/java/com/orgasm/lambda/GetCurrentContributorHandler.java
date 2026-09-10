package com.orgasm.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgasm.dynamo.contributor.ContributorDynamoRepository;
import com.orgasm.dynamo.contributor.ContributorMapper;
import com.orgasm.dynamo.contributor.ContributorResponse;
import com.orgasm.dynamo.tenant.DynamoTenantContext;
import jakarta.validation.Validator;

import java.util.NoSuchElementException;

/**
 * The {@code /me} equivalent explicitly excluded from the earlier no-auth pass — there was no
 * caller identity to resolve it from then. {@code BaseHandler} has already resolved the current
 * Contributor by the validated token's {@code sub} (the default {@code AuthMode} requires it) and
 * set {@link DynamoTenantContext} from it before {@code execute} runs; look the same item up
 * again here rather than threading it through as a field, keeping this handler's shape
 * consistent with every other read handler.
 */
public class GetCurrentContributorHandler extends BaseHandler<ContributorResponse> {

    private final ContributorDynamoRepository contributorRepository;
    private final ContributorMapper mapper;

    public GetCurrentContributorHandler() {
        var ctx = SpringContextHolder.get();
        this.contributorRepository = ctx.getBean(ContributorDynamoRepository.class);
        this.mapper = ctx.getBean(ContributorMapper.class);
    }

    GetCurrentContributorHandler(ContributorDynamoRepository contributorRepository, ContributorMapper mapper,
            ObjectMapper objectMapper, Validator validator) {
        super(objectMapper, validator);
        this.contributorRepository = contributorRepository;
        this.mapper = mapper;
    }

    @Override
    protected ContributorResponse execute(APIGatewayV2HTTPEvent event) {
        return contributorRepository.findByCognitoSub(cognitoSub)
                .filter(c -> c.getDeletedAt() == null)
                .map(mapper::toResponse)
                .orElseThrow(() -> new NoSuchElementException("Contributor not found"));
    }
}
