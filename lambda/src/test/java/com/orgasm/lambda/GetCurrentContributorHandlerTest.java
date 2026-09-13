package com.orgasm.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgasm.dynamo.contributor.ContributorDynamoRepository;
import com.orgasm.dynamo.contributor.ContributorItem;
import com.orgasm.dynamo.contributor.ContributorMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static com.orgasm.lambda.LambdaTestSupport.VALIDATOR;
import static com.orgasm.lambda.LambdaTestSupport.getEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetCurrentContributorHandlerTest {

    @Mock ContributorDynamoRepository contributorRepository;
    private final ContributorMapper mapper = new ContributorMapper();
    private final Context context = mock(Context.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private GetCurrentContributorHandler handler() {
        return new GetCurrentContributorHandler(contributorRepository, mapper, objectMapper, VALIDATOR);
    }

    @Test
    void returns404_whenNoContributorLinked() {
        when(contributorRepository.findByCognitoSub(null)).thenReturn(Optional.empty());

        var response = handler().handleRequest(getEvent(), context);

        assertThat(response.getStatusCode()).isEqualTo(404);
    }

    @Test
    void returns404_whenLinkedContributorIsSoftDeleted() {
        var deleted = new ContributorItem();
        deleted.setDeletedAt(Instant.now());
        when(contributorRepository.findByCognitoSub(null)).thenReturn(Optional.of(deleted));

        var response = handler().handleRequest(getEvent(), context);

        assertThat(response.getStatusCode()).isEqualTo(404);
    }

    @Test
    void returns200_withMappedContributor() {
        var item = new ContributorItem();
        item.setId(1L);
        item.setName("Alice");
        when(contributorRepository.findByCognitoSub(null)).thenReturn(Optional.of(item));

        var response = handler().handleRequest(getEvent(), context);

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getBody()).contains("Alice");
    }
}
