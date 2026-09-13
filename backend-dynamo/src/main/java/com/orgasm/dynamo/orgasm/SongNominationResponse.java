package com.orgasm.dynamo.orgasm;

import com.orgasm.dynamo.nomination.NominationStatus;
import lombok.Builder;

@Builder
public record SongNominationResponse(
        String id,
        String playlistId,
        String playlistName,
        String nominatedById,
        String nominatedByName,
        NominationStatus status
) {}
