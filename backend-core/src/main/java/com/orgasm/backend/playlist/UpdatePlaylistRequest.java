package com.orgasm.backend.playlist;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Builder;

import java.time.Instant;

@Builder
public record UpdatePlaylistRequest(
        @NotBlank String name,
        String description,
        PlaylistStatus status,
        @FutureOrPresent Instant deadline,
        @FutureOrPresent Instant guessingDeadline,
        @Positive @Max(3) Integer maxNominationsPerContributor
) {}
