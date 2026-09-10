package com.orgasm.dynamo.orgasm;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

import java.util.List;

@Builder
public record SubmitGuessesRequest(
        @NotBlank String contributorId,
        List<GuessSelection> guesses
) {
    @Builder
    public record GuessSelection(
            @NotBlank String nominationId,
            @NotBlank String guessedContributorId
    ) {}
}
