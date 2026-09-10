package com.orgasm.dynamo.orgasm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.time.Instant;

@Builder
public record OpenPlaylistRequest(
        @NotBlank String contributorId,
        @NotNull Instant deadline
) {}
