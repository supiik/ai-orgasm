package com.etactics.proxima.sal.backend.orgasm;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record PublishPlaylistRequest(@NotBlank String contributorId) {}
