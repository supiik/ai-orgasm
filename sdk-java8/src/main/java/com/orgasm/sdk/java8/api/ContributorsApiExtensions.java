package com.orgasm.sdk.java8.api;

import com.orgasm.sdk.java8.ApiClient;
import com.orgasm.sdk.java8.ApiException;
import com.orgasm.sdk.java8.model.CreateContributorRequest;
import com.orgasm.sdk.java8.model.ContributorResponse;
import com.orgasm.sdk.java8.model.UpdateContributorRequest;

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

    public ContributorResponse updateContributor(Long id, UnaryOperator<UpdateContributorRequest.UpdateContributorRequestBuilder> customizer) throws ApiException {
        return updateContributor(id, customizer.apply(UpdateContributorRequest.builder()).build());
    }
}
