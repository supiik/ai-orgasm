package com.orgasm.dynamo.orgasm;

import lombok.Builder;

@Builder
public record SongRatingResponse(
        String nominationId,
        String contributorId,
        String contributorName,
        int points
) {}
