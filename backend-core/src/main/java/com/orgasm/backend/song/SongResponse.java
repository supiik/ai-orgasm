package com.orgasm.backend.song;

import lombok.Builder;

import java.time.Instant;

@Builder
public record SongResponse(
        String id,
        String artist,
        String name,
        String album,
        Integer releaseYear,
        String url,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {}
