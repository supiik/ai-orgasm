package com.orgasm.backend.spotify;

import lombok.Builder;

@Builder
public record SpotifyAuthorizeResponse(String authorizeUrl) {}
