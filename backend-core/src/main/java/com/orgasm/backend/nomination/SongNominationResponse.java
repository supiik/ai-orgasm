package com.orgasm.backend.nomination;

public record SongNominationResponse(
        String id,
        String playlistId,
        String playlistName,
        String nominatedById,
        String nominatedByName,
        NominationStatus status
) {}
