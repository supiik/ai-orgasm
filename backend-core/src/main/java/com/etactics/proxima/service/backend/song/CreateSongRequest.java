package com.etactics.proxima.service.backend.song;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record CreateSongRequest(
        @NotBlank String artist,
        @NotBlank String name,
        String album,
        Integer releaseYear
) {}
