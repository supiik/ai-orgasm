package com.orgasm.backend.spotify;

import java.util.List;
import lombok.Builder;

@Builder
public record SpotifyExportResponse(
        String spotifyPlaylistId,
        String spotifyPlaylistUrl,
        int matchedCount,
        List<String> skippedSongs) {}
