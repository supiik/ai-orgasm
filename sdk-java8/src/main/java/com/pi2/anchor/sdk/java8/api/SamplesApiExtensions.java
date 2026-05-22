package com.pi2.anchor.sdk.java8.api;

import com.pi2.anchor.sdk.java8.ApiClient;
import com.pi2.anchor.sdk.java8.ApiException;
import com.pi2.anchor.sdk.java8.model.CreateSampleRequest;
import com.pi2.anchor.sdk.java8.model.SampleResponse;
import com.pi2.anchor.sdk.java8.model.UpdateSampleRequest;

import java.util.function.UnaryOperator;

public class SamplesApiExtensions extends SamplesApi {

    public SamplesApiExtensions() {
        super();
    }

    public SamplesApiExtensions(ApiClient apiClient) {
        super(apiClient);
    }

    public SampleResponse createSample(UnaryOperator<CreateSampleRequest.CreateSampleRequestBuilder> customizer) throws ApiException {
        return createSample(customizer.apply(CreateSampleRequest.builder()).build());
    }

    public SampleResponse updateSample(String id, UnaryOperator<UpdateSampleRequest.UpdateSampleRequestBuilder> customizer) throws ApiException {
        return updateSample(id, customizer.apply(UpdateSampleRequest.builder()).build());
    }
}
