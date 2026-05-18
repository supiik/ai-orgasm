package com.orgasm.backend.contributor;

import lombok.Builder;

import java.time.Instant;

@Builder
public record ContributorResponse(
        Long id,
        String name,
        String email,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {}
