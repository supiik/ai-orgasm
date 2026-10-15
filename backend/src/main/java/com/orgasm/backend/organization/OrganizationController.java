package com.orgasm.backend.organization;

import com.orgasm.backend.contributor.ContributorService;
import com.orgasm.backend.domain.ForbiddenException;
import com.orgasm.backend.tenant.TenantContext;
import com.orgasm.billing.domain.Organization;
import com.orgasm.billing.repository.OrganizationRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/v1/organizations", version = "1")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationRepository organizationRepository;
    private final ContributorService contributorService;

    public record InviteLinkResponse(String inviteToken) {}

    public record OrganizationSummary(String slug, String name) {}

    @GetMapping
    public ResponseEntity<List<Organization>> findAll() {
        return ResponseEntity.ok(organizationRepository.findAll());
    }

    /** Public — reached by a caller who has no linked Contributor/tenant yet (see CLAUDE.md "Public registration endpoints"). */
    @GetMapping("/by-invite/{token}")
    public ResponseEntity<OrganizationSummary> findByInviteToken(@PathVariable String token) {
        return organizationRepository.findByInviteToken(token)
                .map(org -> ResponseEntity.ok(new OrganizationSummary(org.getSlug(), org.getName())))
                .orElse(ResponseEntity.notFound().build());
    }

    // @AuthenticationPrincipal Jwt (not JwtAuthenticationToken) below — see CLAUDE.md "Spotify
    // playlist export" for why the token-wrapper type silently resolves to null instead of
    // throwing (the same gotcha ContributorController.setOrgAdmin avoids the same way).
    @GetMapping("/invite-link")
    public ResponseEntity<InviteLinkResponse> getInviteLink(@AuthenticationPrincipal Jwt jwt) {
        requireOrgAdmin(jwt);
        return ResponseEntity.ok(new InviteLinkResponse(requireCurrentOrg().getInviteToken()));
    }

    /** Generating a new token invalidates any previously shared link — see Organization.inviteToken. */
    @PostMapping("/invite-link")
    public ResponseEntity<InviteLinkResponse> createInviteLink(@AuthenticationPrincipal Jwt jwt) {
        requireOrgAdmin(jwt);
        Organization org = requireCurrentOrg();
        org.setInviteToken(UUID.randomUUID().toString().replace("-", ""));
        organizationRepository.save(org);
        return ResponseEntity.ok(new InviteLinkResponse(org.getInviteToken()));
    }

    @DeleteMapping("/invite-link")
    public ResponseEntity<Void> revokeInviteLink(@AuthenticationPrincipal Jwt jwt) {
        requireOrgAdmin(jwt);
        Organization org = requireCurrentOrg();
        org.setInviteToken(null);
        organizationRepository.save(org);
        return ResponseEntity.noContent().build();
    }

    private void requireOrgAdmin(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (!contributorService.isOrgAdmin(email)) {
            throw new ForbiddenException("Only an organization admin can do this");
        }
    }

    private Organization requireCurrentOrg() {
        Long tenantId = TenantContext.get();
        return organizationRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Organization not found: " + tenantId));
    }
}
