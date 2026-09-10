package com.orgasm.dynamo.song;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record CreateSongRequest(
        @NotBlank String artist,
        @NotBlank String name,
        String album,
        Integer releaseYear,
        String url
) {}
