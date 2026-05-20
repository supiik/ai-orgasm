package com.etactics.proxima.service.sdk.client.api;

import com.etactics.proxima.service.sdk.client.ApiClient;
import com.etactics.proxima.service.sdk.client.ApiException;
import com.etactics.proxima.service.sdk.model.CreateContributorRequest;
import com.etactics.proxima.service.sdk.model.ContributorResponse;
import com.etactics.proxima.service.sdk.model.UpdateContributorRequest;

import java.util.function.UnaryOperator;

public class ContributorsApiExtensions extends ContributorsApi {

    public ContributorsApiExtensions() {
        super();
    }

    public ContributorsApiExtensions(ApiClient apiClient) {
        super(apiClient);
    }

    public ContributorResponse createContributor(UnaryOperator<CreateContributorRequest.CreateContributorRequestBuilder> customizer) throws ApiException {
        return createContributor(customizer.apply(CreateContributorRequest.builder()).build());
    }

    public ContributorResponse updateContributor(String id, UnaryOperator<UpdateContributorRequest.UpdateContributorRequestBuilder> customizer) throws ApiException {
        return updateContributor(id, customizer.apply(UpdateContributorRequest.builder()).build());
    }
}
