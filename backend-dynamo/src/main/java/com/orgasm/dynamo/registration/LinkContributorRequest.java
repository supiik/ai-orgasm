package com.orgasm.dynamo.registration;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

/**
 * Body for the "complete your profile" step after a Cognito sign-up. {@code email} and the
 * Cognito {@code sub} deliberately aren't fields here — the caller (the {@code lambda} module's
 * link handler) passes those into {@link LinkContributorService} separately, sourced from the
 * already-validated JWT, never from client-supplied input — so a caller can't link/impersonate
 * a different email.
 */
@Builder
public record LinkContributorRequest(
        @NotBlank String organizationSlug,
        @NotBlank String name,
        String avatarUrl
) {}
