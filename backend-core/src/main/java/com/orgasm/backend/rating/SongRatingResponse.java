package com.orgasm.backend.rating;

public record SongRatingResponse(
        String nominationId,
        String contributorId,
        String contributorName,
        int points
) {}
