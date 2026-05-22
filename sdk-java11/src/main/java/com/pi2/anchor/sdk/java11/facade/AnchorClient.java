package com.pi2.anchor.sdk.java11.facade;

import com.pi2.anchor.sdk.java11.ApiClient;
import com.pi2.anchor.sdk.java11.api.SamplesApi;

public class AnchorClient {

    private final SampleOperations samples;

    public AnchorClient(String baseUrl) {
        ApiClient http = new ApiClient();
        http.updateBaseUri(baseUrl);
        this.samples = new SampleOperations(new SamplesApi(http));
    }

    AnchorClient(SampleOperations samples) {
        this.samples = samples;
    }

    public SampleOperations samples() { return samples; }
}
