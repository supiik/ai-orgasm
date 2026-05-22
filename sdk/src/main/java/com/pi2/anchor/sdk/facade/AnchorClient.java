package com.pi2.anchor.sdk.facade;

import com.pi2.anchor.sdk.client.ApiClient;
import com.pi2.anchor.sdk.client.api.SamplesApi;

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
