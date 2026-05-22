package com.pi2.anchor.sdk.java11.facade;

import com.pi2.anchor.sdk.java11.api.SamplesApi;
import com.pi2.anchor.sdk.model.CreateSampleRequest;
import com.pi2.anchor.sdk.model.SamplePage;
import com.pi2.anchor.sdk.model.SampleResponse;
import com.pi2.anchor.sdk.model.SampleStatus;
import com.pi2.anchor.sdk.model.UpdateSampleRequest;

public class SampleOperations {

    private final SamplesApi api;

    SampleOperations(SamplesApi api) { this.api = api; }

    public SamplePage     list(int page, int size)                                              { return api.findAllSamples(null, null, page, size, null); }
    public SamplePage     list(int page, int size, String sort, String name)                    { return api.findAllSamples(name, null, page, size, sort); }
    public SamplePage     list(int page, int size, String sort, String name, SampleStatus status) { return api.findAllSamples(name, status, page, size, sort); }
    public SampleResponse get(String id)                                                        { return api.getSample(id); }
    public SampleResponse create(CreateSampleRequest request)                                   { return api.createSample(request); }
    public SampleResponse update(String id, UpdateSampleRequest request)                        { return api.updateSample(id, request); }
    public void           delete(String id)                                                     { api.deleteSample(id); }
}
