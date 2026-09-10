package com.orgasm.dynamo.orgasm;

import lombok.Builder;

@Builder
public record GuessResponse(
        String nominationId,
        String guesserId,
        String guessedContributorId
) {}
