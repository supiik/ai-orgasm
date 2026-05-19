package com.orgasm.backend.playlist;

import lombok.Builder;

import java.time.Instant;

@Builder
public record PlaylistResponse(
        String id,
        String name,
        String description,
        PlaylistStatus status,
        String leadContributorId,
        Instant deadline,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {}
