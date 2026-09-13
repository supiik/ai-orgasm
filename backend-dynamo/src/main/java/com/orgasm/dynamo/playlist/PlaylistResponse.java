package com.orgasm.dynamo.playlist;

import lombok.Builder;

import java.time.Instant;

@Builder
public record PlaylistResponse(
        String id,
        String name,
        String description,
        PlaylistStatus status,
        RatingType ratingType,
        String leadContributorId,
        String leadContributorName,
        String leadContributorAvatarUrl,
        Instant deadline,
        Instant guessingDeadline,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {}
