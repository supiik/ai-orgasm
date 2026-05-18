package com.orgasm.backend.contributor;

import lombok.Builder;

import java.time.Instant;

@Builder
public record ContributorResponse(
        String id,
        String name,
        String email,
        String avatarUrl,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {}
