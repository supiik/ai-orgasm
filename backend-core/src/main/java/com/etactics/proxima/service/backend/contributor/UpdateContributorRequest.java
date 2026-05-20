package com.etactics.proxima.service.backend.contributor;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record UpdateContributorRequest(
        @NotBlank String name,
        String email,
        String avatarUrl
) {}
