package com.orgasm.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgasm.dynamo.contributor.ContributorDynamoRepository;
import com.orgasm.dynamo.contributor.ContributorItem;
import com.orgasm.dynamo.tenant.DynamoTenantContext;
import com.orgasm.lambda.auth.AuthMode;
import com.orgasm.lambda.auth.CognitoClaims;
import com.orgasm.lambda.auth.CognitoJwtValidator;
import com.orgasm.lambda.auth.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static com.orgasm.lambda.LambdaTestSupport.VALIDATOR;
import static com.orgasm.lambda.LambdaTestSupport.authenticatedEvent;
import static com.orgasm.lambda.LambdaTestSupport.event;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BaseHandlerAuthTest {

    @Mock CognitoJwtValidator cognitoJwtValidator;
    @Mock ContributorDynamoRepository contributorRepository;
    private final Context context = mock(Context.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    private TestHandler handler(AuthMode mode) {
        return new TestHandler(mode, mapper, VALIDATOR, cognitoJwtValidator, contributorRepository);
    }

    @Test
    void publicMode_skipsAuthEntirely() {
        var response = handler(AuthMode.PUBLIC).handleRequest(event("GET", "/anything", null), context);

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"sub\":null");
        verifyNoInteractions(cognitoJwtValidator, contributorRepository);
    }

    @Test
    void authenticated_returns401_whenAuthorizationHeaderMissing() {
        var response = handler(AuthMode.AUTHENTICATED).handleRequest(event("GET", "/anything", null), context);

        assertThat(response.getStatusCode()).isEqualTo(401);
    }

    @Test
    void authenticated_returns401_whenHeaderNotBearer() {
        var event = APIGatewayV2HTTPEvent.builder()
                .withRequestContext(APIGatewayV2HTTPEvent.RequestContext.builder()
                        .withHttp(APIGatewayV2HTTPEvent.RequestContext.Http.builder().withMethod("GET").build())
                        .build())
                .withRawPath("/anything")
                .withHeaders(Map.of("Authorization", "Basic dXNlcjpwYXNz"))
                .build();

        var response = handler(AuthMode.AUTHENTICATED).handleRequest(event, context);

        assertThat(response.getStatusCode()).isEqualTo(401);
    }

    @Test
    void authenticated_returns401_whenTokenInvalid() {
        when(cognitoJwtValidator.validate("bad-token")).thenThrow(new UnauthorizedException("Invalid token"));

        var response = handler(AuthMode.AUTHENTICATED)
                .handleRequest(authenticatedEvent("GET", "/anything", null, "bad-token"), context);

        assertThat(response.getStatusCode()).isEqualTo(401);
    }

    @Test
    void authenticated_populatesClaims_withoutResolvingContributor() {
        when(cognitoJwtValidator.validate("good-token")).thenReturn(new CognitoClaims("sub-1", "a@b.com"));

        var response = handler(AuthMode.AUTHENTICATED)
                .handleRequest(authenticatedEvent("GET", "/anything", null, "good-token"), context);

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"sub\":\"sub-1\"");
        verifyNoInteractions(contributorRepository);
    }

    @Test
    void authenticatedWithContributor_returns403_whenNoContributorLinked() {
        when(cognitoJwtValidator.validate("good-token")).thenReturn(new CognitoClaims("sub-1", "a@b.com"));
        when(contributorRepository.findByCognitoSub("sub-1")).thenReturn(Optional.empty());

        var response = handler(AuthMode.AUTHENTICATED_WITH_CONTRIBUTOR)
                .handleRequest(authenticatedEvent("GET", "/anything", null, "good-token"), context);

        assertThat(response.getStatusCode()).isEqualTo(403);
    }

    @Test
    void authenticatedWithContributor_returns403_whenLinkedContributorIsSoftDeleted() {
        var deleted = new ContributorItem();
        deleted.setTenantId(7L);
        deleted.setDeletedAt(Instant.now());
        when(cognitoJwtValidator.validate("good-token")).thenReturn(new CognitoClaims("sub-1", "a@b.com"));
        when(contributorRepository.findByCognitoSub("sub-1")).thenReturn(Optional.of(deleted));

        var response = handler(AuthMode.AUTHENTICATED_WITH_CONTRIBUTOR)
                .handleRequest(authenticatedEvent("GET", "/anything", null, "good-token"), context);

        assertThat(response.getStatusCode()).isEqualTo(403);
    }

    @Test
    void authenticatedWithContributor_setsTenantContextDuringExecute_andClearsItAfter() {
        var contributor = new ContributorItem();
        contributor.setTenantId(42L);
        when(cognitoJwtValidator.validate("good-token")).thenReturn(new CognitoClaims("sub-1", "a@b.com"));
        when(contributorRepository.findByCognitoSub("sub-1")).thenReturn(Optional.of(contributor));

        var response = handler(AuthMode.AUTHENTICATED_WITH_CONTRIBUTOR)
                .handleRequest(authenticatedEvent("GET", "/anything", null, "good-token"), context);

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"tenantIdDuringExecute\":42");
        assertThat(DynamoTenantContext.get()).isEqualTo(1L); // default — cleared after the request
    }

    private record TestBody(String sub, Long tenantIdDuringExecute) {}

    /** Minimal concrete handler exercising every AuthMode branch. */
    private static class TestHandler extends BaseHandler<TestBody> {

        private final AuthMode mode;

        TestHandler(AuthMode mode, ObjectMapper mapper, jakarta.validation.Validator validator,
                CognitoJwtValidator cognitoJwtValidator, ContributorDynamoRepository contributorRepository) {
            super(mapper, validator, cognitoJwtValidator, contributorRepository);
            this.mode = mode;
        }

        @Override
        protected TestBody execute(APIGatewayV2HTTPEvent event) {
            return new TestBody(cognitoSub, DynamoTenantContext.get());
        }

        @Override
        protected AuthMode authMode() {
            return mode;
        }
    }
}
