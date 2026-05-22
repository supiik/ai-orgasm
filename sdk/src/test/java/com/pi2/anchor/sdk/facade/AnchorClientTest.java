package com.pi2.anchor.sdk.facade;

import com.pi2.anchor.sdk.client.api.SamplesApi;
import com.pi2.anchor.sdk.model.*;
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
        var page = new SamplePage();
        when(samplesApi.findAllSamples(null, null, 0, 20, null)).thenReturn(page);
        assertThat(client.samples().list(0, 20)).isSameAs(page);
    }

    @Test void samples_list_withNameFilter() {
        var page = new SamplePage();
        when(samplesApi.findAllSamples("widget", null, 0, 10, "name")).thenReturn(page);
        assertThat(client.samples().list(0, 10, "name", "widget")).isSameAs(page);
    }

    @Test void samples_get() {
        var r = new SampleResponse();
        when(samplesApi.getSample("smpl-0001")).thenReturn(r);
        assertThat(client.samples().get("smpl-0001")).isSameAs(r);
    }

    @Test void samples_create() {
        var req = CreateSampleRequest.builder().name("My Widget").status(SampleStatus.DRAFT).build();
        var r = new SampleResponse();
        when(samplesApi.createSample(req)).thenReturn(r);
        assertThat(client.samples().create(req)).isSameAs(r);
    }

    @Test void samples_update() {
        var req = UpdateSampleRequest.builder().name("Updated Widget").build();
        var r = new SampleResponse();
        when(samplesApi.updateSample("smpl-0002", req)).thenReturn(r);
        assertThat(client.samples().update("smpl-0002", req)).isSameAs(r);
    }

    @Test void samples_delete() {
        client.samples().delete("smpl-0003");
        verify(samplesApi).deleteSample("smpl-0003");
    }
}
