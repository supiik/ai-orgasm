package com.orgasm.backend.spotify;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Keeps a {@link SpotifyConnection}'s access token usable: refreshes it before it expires, and
 * retries once (after a forced refresh) if Spotify still rejects it with 401 — the same
 * refresh-before-use-plus-retry-once shape the TypeScript port of this feature uses. */
@Service
@RequiredArgsConstructor
@Transactional("appTransactionManager")
public class SpotifyTokenService {

    private static final Duration REFRESH_SKEW = Duration.ofSeconds(60);

    private final SpotifyConnectionRepository connectionRepository;
    private final SpotifyClient spotifyClient;

    /** Runs {@code action} with a valid access token, refreshing first if it's near expiry and
     * retrying once (after a forced refresh) if Spotify rejects the token with 401. */
    public <T> T call(SpotifyConnection connection, Function<String, T> action) {
        String token = ensureFreshToken(connection);
        try {
            return action.apply(token);
        } catch (SpotifyUnauthorizedException ex) {
            return action.apply(forceRefresh(connection));
        }
    }

    private String ensureFreshToken(SpotifyConnection connection) {
        if (Instant.now().isAfter(connection.getExpiresAt().minus(REFRESH_SKEW))) {
            return forceRefresh(connection);
        }
        return connection.getAccessToken();
    }

    private String forceRefresh(SpotifyConnection connection) {
        SpotifyTokens tokens = spotifyClient.refreshToken(connection.getRefreshToken());
        connection.setAccessToken(tokens.accessToken());
        connection.setRefreshToken(tokens.refreshToken());
        connection.setExpiresAt(tokens.expiresAt());
        connectionRepository.save(connection);
        return connection.getAccessToken();
    }
}
