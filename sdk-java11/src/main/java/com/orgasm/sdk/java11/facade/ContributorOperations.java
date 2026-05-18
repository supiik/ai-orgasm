package com.orgasm.sdk.java11.facade;

import com.orgasm.sdk.java11.api.ContributorsApi;
import com.orgasm.sdk.model.ContributorPage;
import com.orgasm.sdk.model.ContributorResponse;
import com.orgasm.sdk.model.CreateContributorRequest;
import com.orgasm.sdk.model.UpdateContributorRequest;

public class ContributorOperations {

    private final ContributorsApi api;

    ContributorOperations(ContributorsApi api) { this.api = api; }

    public ContributorPage     list(int page, int size)                              { return api.findAllContributors(page, size, "id", null); }
    public ContributorPage     list(int page, int size, String sort, String name)    { return api.findAllContributors(page, size, sort, name); }
    public ContributorResponse get(long id)                                          { return api.findContributorById(id); }
    public ContributorResponse create(CreateContributorRequest request)              { return api.createContributor(request); }
    public ContributorResponse update(long id, UpdateContributorRequest request)     { return api.updateContributor(id, request); }
    public void                delete(long id)                                       { api.deleteContributor(id); }
}
