package com.orgasm.backend.orgasm;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.List;

@Builder
public record SubmitGuessesRequest(
        @NotBlank String contributorId,
        @NotNull @Valid List<GuessItem> guesses) {

    @Builder
    public record GuessItem(
            @NotBlank String nominationId,
            @NotBlank String guessedContributorId) {}
}
