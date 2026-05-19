package com.orgasm.backend.orgasm;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record PublishPlaylistRequest(@NotBlank String contributorId) {}
