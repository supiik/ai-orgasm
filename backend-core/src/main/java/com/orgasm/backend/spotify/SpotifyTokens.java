package com.orgasm.backend.spotify;

import java.time.Instant;

/** Access/refresh token pair returned by Spotify's token endpoint. {@code refreshToken} is
 * {@code null} when Spotify's refresh response omitted a rotated refresh token (the caller
 * should keep using the previous one in that case). */
public record SpotifyTokens(String accessToken, String refreshToken, Instant expiresAt) {}
