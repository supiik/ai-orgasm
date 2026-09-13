package com.orgasm.backend.registration;

import com.orgasm.backend.contributor.ContributorResponse;
import com.orgasm.backend.contributor.ContributorService;
import com.orgasm.billing.domain.Organization;
import com.orgasm.billing.repository.OrganizationRepository;
import com.orgasm.backend.tenant.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping(value = "/api/v1/register", version = "1")
@RequiredArgsConstructor
public class RegistrationController {

    private final OrganizationRepository organizationRepository;
    private final ContributorService contributorService;

    public record RegisterRequest(
            @NotBlank String name,
            String email,
            String avatarUrl,
            @NotBlank String organizationSlug
    ) {}

    @PostMapping
    public ResponseEntity<ContributorResponse> register(@RequestBody @Valid RegisterRequest request) {
        Organization org = organizationRepository.findBySlug(request.organizationSlug())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Organization not found: " + request.organizationSlug()));
        assertEmailAllowed(org, request.email());

        TenantContext.set(org.getId());
        try {
            ContributorResponse created = contributorService.create(b -> b
                    .name(request.name())
                    .email(request.email())
                    .avatarUrl(request.avatarUrl()));
            URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/api/v1/contributors/{id}")
                    .buildAndExpand(created.id())
                    .toUri();
            return ResponseEntity.created(location).body(created);
        } finally {
            TenantContext.clear();
        }
    }

    /** No-op when the organization has no {@code allowedDomain} configured (default: unrestricted). */
    private static void assertEmailAllowed(Organization org, String email) {
        String allowedDomain = org.getAllowedDomain();
        if (allowedDomain == null || allowedDomain.isBlank()) {
            return;
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException(
                    "Email required: organization \"" + org.getSlug() + "\" only accepts @" + allowedDomain + " addresses");
        }
        String domain = email.substring(email.lastIndexOf('@') + 1);
        if (!domain.equalsIgnoreCase(allowedDomain)) {
            throw new IllegalArgumentException(
                    "Email domain does not match organization \"" + org.getSlug() + "\"'s allowed domain (@" + allowedDomain + ")");
        }
    }
}
