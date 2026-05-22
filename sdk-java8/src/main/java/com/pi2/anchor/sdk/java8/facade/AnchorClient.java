package com.pi2.anchor.sdk.java8.facade;

import com.pi2.anchor.sdk.java8.ApiClient;
import com.pi2.anchor.sdk.java8.api.SamplesApi;

public class AnchorClient {

    private final SampleOperations samples;

    public AnchorClient(String baseUrl) {
        ApiClient http = new ApiClient();
        http.setBasePath(baseUrl);
        this.samples = new SampleOperations(new SamplesApi(http));
    }

    AnchorClient(SampleOperations samples) {
        this.samples = samples;
    }

    public SampleOperations samples() { return samples; }
}
