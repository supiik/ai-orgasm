package com.pi2.anchor.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pi2.anchor.backend.sample.CreateSampleRequest;
import com.pi2.anchor.backend.sample.SampleResponse;
import com.pi2.anchor.backend.sample.SampleService;
import com.pi2.anchor.backend.sample.SampleStatus;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static com.pi2.anchor.lambda.LambdaTestSupport.VALIDATOR;
import static com.pi2.anchor.lambda.LambdaTestSupport.postEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateSampleHandlerTest {

    @Mock SampleService sampleService;
    private final Context context = mock(Context.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    private CreateSampleHandler handler() {
        return new CreateSampleHandler(sampleService, mapper, VALIDATOR);
    }

    private static SampleResponse stubResponse() {
        return new SampleResponse("smpl-0001", "Widget", null, null, 0, 0L, 0.0, null, true,
                null, null, SampleStatus.DRAFT, null, 0L, Instant.EPOCH, Instant.EPOCH);
    }

    @SuppressWarnings("unchecked")
    private static CreateSampleRequest anyRequest() {
        return (CreateSampleRequest) any();
    }

    @Test
    void returns201_withCreatedSample() {
        when(sampleService.create(anyRequest())).thenReturn(stubResponse());
        var event = postEvent("{\"name\":\"Widget\",\"status\":\"DRAFT\"}");

        var response = handler().handleRequest(event, context);

        assertThat(response.getStatusCode()).isEqualTo(201);
        assertThat(response.getBody()).contains("Widget");
    }

    @Test
    void returns400_whenNameBlank() {
        var event = postEvent("{\"name\":\"\",\"status\":\"DRAFT\"}");

        var response = handler().handleRequest(event, context);

        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getBody()).contains("errors");
    }

    @Test
    void returns400_whenBodyMalformedJson() {
        var event = postEvent("not-json");

        var response = handler().handleRequest(event, context);

        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getBody()).contains("error");
    }

    @Test
    void returns404_whenServiceThrowsNotFound() {
        when(sampleService.create(anyRequest())).thenThrow(new EntityNotFoundException("Sample not found: 99"));
        var event = postEvent("{\"name\":\"Widget\",\"status\":\"DRAFT\"}");

        var response = handler().handleRequest(event, context);

        assertThat(response.getStatusCode()).isEqualTo(404);
        assertThat(response.getBody()).contains("Sample not found: 99");
    }

    @Test
    void returns500_whenServiceThrowsUnexpected() {
        when(sampleService.create(anyRequest())).thenThrow(new RuntimeException("boom"));
        var event = postEvent("{\"name\":\"Widget\",\"status\":\"DRAFT\"}");

        var response = handler().handleRequest(event, context);

        assertThat(response.getStatusCode()).isEqualTo(500);
        assertThat(response.getBody()).contains("Internal server error");
    }

    @SuppressWarnings("unchecked")
    @Test
    void returns500_whenSerializationFails() throws Exception {
        var failingMapper = mock(ObjectMapper.class);
        when(failingMapper.readValue(any(String.class), any(Class.class)))
                .thenReturn(new CreateSampleRequest("x", null, null, 0, 0L, 0.0, null, true, null, null, SampleStatus.DRAFT, null));
        when(failingMapper.writeValueAsString(any())).thenThrow(new RuntimeException("ser-fail"));
        var handler = new CreateSampleHandler(sampleService, failingMapper, VALIDATOR);

        var response = handler.handleRequest(postEvent("{\"name\":\"x\",\"status\":\"DRAFT\"}"), context);

        assertThat(response.getStatusCode()).isEqualTo(500);
        assertThat(response.getBody()).contains("Serialization failed");
    }
}
