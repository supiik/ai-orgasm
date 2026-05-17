package com.orgasm.backend.playlist;

import java.time.Instant;

public record PlaylistResponse(
        Long id,
        String name,
        String description,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {}
