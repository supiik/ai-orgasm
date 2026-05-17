package com.orgasm.backend.playlist;

import jakarta.validation.constraints.NotBlank;

public record CreatePlaylistRequest(
        @NotBlank String name,
        String description
) {}
