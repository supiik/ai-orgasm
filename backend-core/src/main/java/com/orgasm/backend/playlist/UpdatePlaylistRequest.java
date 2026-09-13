package com.orgasm.backend.playlist;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

import java.time.Instant;

@Builder
public record UpdatePlaylistRequest(
        @NotBlank String name,
        String description,
        PlaylistStatus status,
        Instant deadline,
        Instant guessingDeadline
) {}
