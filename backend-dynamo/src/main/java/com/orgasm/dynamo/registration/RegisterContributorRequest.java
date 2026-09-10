package com.orgasm.dynamo.registration;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record RegisterContributorRequest(
        @NotBlank String organizationSlug,
        @NotBlank String name,
        String email,
        String avatarUrl
) {}
