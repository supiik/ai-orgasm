package com.orgasm.dynamo.orgasm;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record StartGuessingRequest(
        @NotBlank String contributorId
) {}
