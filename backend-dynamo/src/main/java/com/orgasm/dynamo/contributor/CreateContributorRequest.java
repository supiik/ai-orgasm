package com.orgasm.dynamo.contributor;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record CreateContributorRequest(
        @NotBlank String name,
        String email,
        String avatarUrl
) {}
