package com.orgasm.backend.spotify;

/** Thrown when a call to the Spotify Web API fails (network error, non-2xx response). */
public class SpotifyUpstreamException extends RuntimeException {

    public SpotifyUpstreamException(String message, Throwable cause) {
        super(message, cause);
    }
}
