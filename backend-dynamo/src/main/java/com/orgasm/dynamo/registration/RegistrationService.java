package com.orgasm.dynamo.registration;

import com.orgasm.dynamo.contributor.ContributorResponse;
import com.orgasm.dynamo.contributor.ContributorService;
import com.orgasm.dynamo.contributor.CreateContributorRequest;
import com.orgasm.dynamo.organization.OrganizationDynamoRepository;
import com.orgasm.dynamo.tenant.DynamoTenantContext;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final OrganizationDynamoRepository organizationRepository;
    private final ContributorService contributorService;

    @CircuitBreaker(name = "db")
    public ContributorResponse register(RegisterContributorRequest request) {
        var organization = organizationRepository.findBySlug(request.organizationSlug())
                .orElseThrow(() -> new NoSuchElementException("Organization not found: " + request.organizationSlug()));

        DynamoTenantContext.set(organization.getId());
        try {
            return contributorService.create(CreateContributorRequest.builder()
                    .name(request.name())
                    .email(request.email())
                    .avatarUrl(request.avatarUrl())
                    .build());
        } finally {
            DynamoTenantContext.clear();
        }
    }
}
