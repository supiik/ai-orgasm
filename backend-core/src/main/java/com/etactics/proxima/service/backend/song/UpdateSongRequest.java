package com.etactics.proxima.service.backend.song;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record UpdateSongRequest(
        @NotBlank String artist,
        @NotBlank String name,
        String album,
        Integer releaseYear
) {}
