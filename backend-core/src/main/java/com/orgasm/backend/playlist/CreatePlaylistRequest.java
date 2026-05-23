package com.orgasm.backend.playlist;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record CreatePlaylistRequest(
        @NotBlank String name,
        String description,
        PlaylistStatus status,
        RatingType ratingType
) {}
