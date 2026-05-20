package com.etactics.proxima.service.backend.playlist;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record CreatePlaylistRequest(
        @NotBlank String name,
        String description,
        PlaylistStatus status
) {}
