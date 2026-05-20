package com.etactics.proxima.service.backend.orgasm;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record NominateSongRequest(
        @NotBlank String contributorId,
        @NotBlank String songId
) {}
