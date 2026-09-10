package com.orgasm.dynamo.orgasm;

import lombok.Builder;

@Builder
public record RankingResponse(
        String playlistId,
        String playlistName,
        String contributorId,
        String contributorName,
        String contributorAvatarUrl,
        int rankPosition,
        int correctGuesses,
        int totalGuesses
) {}
