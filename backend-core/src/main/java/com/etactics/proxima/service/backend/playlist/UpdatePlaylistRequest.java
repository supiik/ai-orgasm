package com.etactics.proxima.service.backend.playlist;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record UpdatePlaylistRequest(
        @NotBlank String name,
        String description,
        PlaylistStatus status
) {}
