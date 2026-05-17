package com.orgasm.backend.playlist;

import jakarta.validation.constraints.NotBlank;

public record UpdatePlaylistRequest(
        @NotBlank String name,
        String description
) {}
