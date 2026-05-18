package com.orgasm.backend.contributor;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record UpdateContributorRequest(
        @NotBlank String name,
        String email
) {}
