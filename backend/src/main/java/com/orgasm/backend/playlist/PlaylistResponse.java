package com.orgasm.backend.playlist;

import lombok.Builder;

import java.time.Instant;

@Builder
public record PlaylistResponse(
        Long id,
        String name,
        String description,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {}
