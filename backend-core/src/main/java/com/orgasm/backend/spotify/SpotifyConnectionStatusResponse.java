package com.orgasm.backend.spotify;

import lombok.Builder;

@Builder
public record SpotifyConnectionStatusResponse(boolean connected) {}
