package com.etactics.proxima.sal.sdk.java11.api;

import com.etactics.proxima.sal.sdk.java11.ApiClient;
import com.etactics.proxima.sal.sdk.java11.ApiException;
import com.etactics.proxima.sal.sdk.model.CreateContributorRequest;
import com.etactics.proxima.sal.sdk.model.ContributorResponse;
import com.etactics.proxima.sal.sdk.model.UpdateContributorRequest;

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
