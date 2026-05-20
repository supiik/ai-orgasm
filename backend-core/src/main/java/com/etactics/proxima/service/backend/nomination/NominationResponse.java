package com.etactics.proxima.service.backend.nomination;

import lombok.Builder;

import java.time.Instant;

@Builder
public record NominationResponse(
        String id,
        String playlistId,
        String songId,
        String nominatedById,
        NominationStatus status,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {}
