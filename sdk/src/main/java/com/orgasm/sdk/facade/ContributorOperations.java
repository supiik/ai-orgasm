package com.orgasm.sdk.facade;

import com.orgasm.sdk.client.api.ContributorsApi;
import com.orgasm.sdk.model.ContributorPage;
import com.orgasm.sdk.model.ContributorResponse;
import com.orgasm.sdk.model.CreateContributorRequest;
import com.orgasm.sdk.model.UpdateContributorRequest;

public class ContributorOperations {

    private final ContributorsApi api;

    ContributorOperations(ContributorsApi api) { this.api = api; }

    public ContributorPage     list(int page, int size)                              { return api.findAllContributors(page, size, "id", null); }
    public ContributorPage     list(int page, int size, String sort, String name)    { return api.findAllContributors(page, size, sort, name); }
    public ContributorResponse get(String id)                                        { return api.findContributorById(id); }
    public ContributorResponse create(CreateContributorRequest request)              { return api.createContributor(request); }
    public ContributorResponse update(String id, UpdateContributorRequest request)   { return api.updateContributor(id, request); }
    public void                delete(String id)                                     { api.deleteContributor(id); }
}
