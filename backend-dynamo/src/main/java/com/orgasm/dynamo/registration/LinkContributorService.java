package com.orgasm.dynamo.registration;

import com.orgasm.dynamo.contributor.ContributorDynamoRepository;
import com.orgasm.dynamo.contributor.ContributorItem;
import com.orgasm.dynamo.contributor.ContributorMapper;
import com.orgasm.dynamo.contributor.ContributorResponse;
import com.orgasm.dynamo.contributor.CreateContributorRequest;
import com.orgasm.dynamo.domain.IdGenerator;
import com.orgasm.dynamo.organization.OrganizationDynamoRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.NoSuchElementException;

/**
 * Links an already-authenticated Cognito identity to a Contributor: attaches to an existing
 * Contributor by email match within the resolved Organization if one exists, otherwise creates
 * a new one. Mirrors {@link RegistrationService} (same org-by-slug resolution, same
 * {@code ContributorDynamoRepository}/{@code ContributorMapper} construction path) but keys off
 * an already-verified {@code cognitoSub}/{@code email} pair instead of being fully public.
 */
@Service
@RequiredArgsConstructor
public class LinkContributorService {

    private final OrganizationDynamoRepository organizationRepository;
    private final ContributorDynamoRepository contributorRepository;
    private final ContributorMapper mapper;

    @CircuitBreaker(name = "db")
    public ContributorResponse link(LinkContributorRequest request, String cognitoSub, String email) {
        var organization = organizationRepository.findBySlug(request.organizationSlug())
                .orElseThrow(() -> new NoSuchElementException("Organization not found: " + request.organizationSlug()));
        long tenantId = organization.getId();

        ContributorItem item = contributorRepository.findByEmail(tenantId, email)
                .map(existing -> attach(existing, cognitoSub))
                .orElseGet(() -> create(request, tenantId, email, cognitoSub));

        return mapper.toResponse(item);
    }

    private ContributorItem attach(ContributorItem existing, String cognitoSub) {
        if (existing.getCognitoSub() != null && !existing.getCognitoSub().equals(cognitoSub)) {
            throw new IllegalStateException("Contributor is already linked to a different account");
        }
        existing.setCognitoSub(cognitoSub);
        existing.setUpdatedAt(Instant.now());
        return contributorRepository.save(existing);
    }

    private ContributorItem create(LinkContributorRequest request, long tenantId, String email, String cognitoSub) {
        long id = IdGenerator.generate();
        Instant now = Instant.now();

        ContributorItem item = mapper.toItem(CreateContributorRequest.builder()
                .name(request.name())
                .email(email)
                .avatarUrl(request.avatarUrl())
                .build(), tenantId, id);
        item.setCognitoSub(cognitoSub);
        item.setCreatedAt(now);
        item.setUpdatedAt(now);

        return contributorRepository.save(item);
    }
}
