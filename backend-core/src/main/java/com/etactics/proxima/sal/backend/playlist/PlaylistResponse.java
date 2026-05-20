package com.etactics.proxima.sal.backend.playlist;

import lombok.Builder;

import java.time.Instant;

@Builder
public record PlaylistResponse(
        String id,
        String name,
        String description,
        PlaylistStatus status,
        String leadContributorId,
        String leadContributorName,
        String leadContributorAvatarUrl,
        Instant deadline,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {}
