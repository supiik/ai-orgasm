package com.orgasm.backend.orgasm;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record SubmitGuessesRequest(@NotBlank String contributorId) {}
