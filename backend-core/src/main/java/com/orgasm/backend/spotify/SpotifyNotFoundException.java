package com.orgasm.backend.spotify;

/** Thrown on a 404 from the Spotify Web API — used by {@link SpotifyService} to detect a stale
 * {@code spotifyPlaylistId} (e.g. the lead deleted the playlist directly in Spotify) and fall
 * back to creating a fresh one. */
public class SpotifyNotFoundException extends SpotifyUpstreamException {

    public SpotifyNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
