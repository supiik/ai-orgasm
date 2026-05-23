package com.orgasm.backend.rating;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record SubmitRatingsRequest(
        @NotBlank String contributorId,
        @NotEmpty List<RatingItem> ratings
) {
    public record RatingItem(@NotBlank String nominationId, int points) {}
}
