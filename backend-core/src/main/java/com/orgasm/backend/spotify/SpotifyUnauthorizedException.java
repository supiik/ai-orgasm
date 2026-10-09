package com.orgasm.backend.spotify;

/**
 * Thrown when Spotify rejects an access token with 401. Caught internally by
 * {@link SpotifyTokenService} to trigger a single refresh-and-retry; if the retry also fails it
 * propagates as a plain {@link SpotifyUpstreamException} (never surfaced as a 401 to our own API
 * callers, which would wrongly suggest their own session is invalid).
 */
public class SpotifyUnauthorizedException extends SpotifyUpstreamException {

    public SpotifyUnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}
