package com.orgasm.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgasm.dynamo.contributor.ContributorResponse;
import com.orgasm.dynamo.registration.LinkContributorRequest;
import com.orgasm.dynamo.registration.LinkContributorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.orgasm.lambda.LambdaTestSupport.VALIDATOR;
import static com.orgasm.lambda.LambdaTestSupport.postEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinkContributorHandlerTest {

    @Mock LinkContributorService linkContributorService;
    private final Context context = mock(Context.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    private LinkContributorHandler handler() {
        return new LinkContributorHandler(linkContributorService, mapper, VALIDATOR);
    }

    private static String body() {
        return "{\"organizationSlug\":\"acme\",\"name\":\"Alice\"}";
    }

    @Test
    void returns200_delegatingToService() {
        when(linkContributorService.link(any(LinkContributorRequest.class), eq(null), eq(null)))
                .thenReturn(ContributorResponse.builder().id("cont-1").name("Alice").build());

        var response = handler().handleRequest(postEvent(body()), context);

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getBody()).contains("Alice");
    }

    @Test
    void returns409_whenAlreadyLinkedToDifferentAccount() {
        when(linkContributorService.link(any(LinkContributorRequest.class), eq(null), eq(null)))
                .thenThrow(new IllegalStateException("Contributor is already linked to a different account"));

        var response = handler().handleRequest(postEvent(body()), context);

        assertThat(response.getStatusCode()).isEqualTo(409);
    }

    @Test
    void returns404_whenOrganizationMissing() {
        when(linkContributorService.link(any(LinkContributorRequest.class), eq(null), eq(null)))
                .thenThrow(new java.util.NoSuchElementException("Organization not found: acme"));

        var response = handler().handleRequest(postEvent(body()), context);

        assertThat(response.getStatusCode()).isEqualTo(404);
    }
}
