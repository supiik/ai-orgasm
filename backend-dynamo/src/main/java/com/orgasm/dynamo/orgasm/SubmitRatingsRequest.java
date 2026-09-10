package com.orgasm.dynamo.orgasm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Builder;

import java.util.List;

@Builder
public record SubmitRatingsRequest(
        @NotBlank String contributorId,
        @NotEmpty List<RatingEntry> ratings
) {
    @Builder
    public record RatingEntry(
            @NotBlank String nominationId,
            int points
    ) {}
}
