package com.pi2.anchor.sdk.java8.facade;

import com.pi2.anchor.sdk.java8.api.SamplesApi;
import com.pi2.anchor.sdk.java8.model.CreateSampleRequest;
import com.pi2.anchor.sdk.java8.model.SamplePage;
import com.pi2.anchor.sdk.java8.model.SampleResponse;
import com.pi2.anchor.sdk.java8.model.SampleStatus;
import com.pi2.anchor.sdk.java8.model.UpdateSampleRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnchorClientTest {

    @Mock SamplesApi samplesApi;

    AnchorClient client;

    @BeforeEach
    void setUp() {
        client = new AnchorClient(new SampleOperations(samplesApi));
    }

    @Test void samples_list_defaultSort() {
        SamplePage page = new SamplePage();
        when(samplesApi.findAllSamples(null, null, 0, 20, null)).thenReturn(page);
        assertThat(client.samples().list(0, 20)).isSameAs(page);
    }

    @Test void samples_list_withNameFilter() {
        SamplePage page = new SamplePage();
        when(samplesApi.findAllSamples("widget", null, 0, 10, "name")).thenReturn(page);
        assertThat(client.samples().list(0, 10, "name", "widget")).isSameAs(page);
    }

    @Test void samples_get() {
        SampleResponse r = new SampleResponse();
        when(samplesApi.getSample("smpl-0001")).thenReturn(r);
        assertThat(client.samples().get("smpl-0001")).isSameAs(r);
    }

    @Test void samples_create() {
        CreateSampleRequest req = CreateSampleRequest.builder().name("My Widget").status(SampleStatus.DRAFT).build();
        SampleResponse r = new SampleResponse();
        when(samplesApi.createSample(req)).thenReturn(r);
        assertThat(client.samples().create(req)).isSameAs(r);
    }

    @Test void samples_update() {
        UpdateSampleRequest req = UpdateSampleRequest.builder().name("Updated Widget").build();
        SampleResponse r = new SampleResponse();
        when(samplesApi.updateSample("smpl-0002", req)).thenReturn(r);
        assertThat(client.samples().update("smpl-0002", req)).isSameAs(r);
    }

    @Test void samples_delete() {
        client.samples().delete("smpl-0003");
        verify(samplesApi).deleteSample("smpl-0003");
    }
}
