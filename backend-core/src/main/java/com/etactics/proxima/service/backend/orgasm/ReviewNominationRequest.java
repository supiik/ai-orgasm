package com.etactics.proxima.service.backend.orgasm;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record ReviewNominationRequest(@NotBlank String reviewerId) {}
