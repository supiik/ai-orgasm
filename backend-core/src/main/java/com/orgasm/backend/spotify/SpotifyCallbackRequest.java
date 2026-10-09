package com.orgasm.backend.spotify;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record SpotifyCallbackRequest(@NotBlank String code, @NotBlank String state) {}
