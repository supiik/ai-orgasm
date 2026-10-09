package com.orgasm.backend.spotify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SpotifyTokenServiceTest {

    @Mock SpotifyConnectionRepository connectionRepository;
    @Mock SpotifyClient spotifyClient;
    @InjectMocks SpotifyTokenService service;

    SpotifyConnection connectionExpiringIn(long seconds) {
        var connection = new SpotifyConnection();
        connection.setAccessToken("access-token");
        connection.setRefreshToken("refresh-token");
        connection.setExpiresAt(Instant.now().plusSeconds(seconds));
        return connection;
    }

    @Test
    void call_usesExistingToken_whenFarFromExpiry() {
        SpotifyConnection connection = connectionExpiringIn(3600);

        String result = service.call(connection, token -> token);

        assertThat(result).isEqualTo("access-token");
        verify(spotifyClient, never()).refreshToken(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void call_refreshesFirst_whenNearExpiry() {
        SpotifyConnection connection = connectionExpiringIn(30);
        when(spotifyClient.refreshToken("refresh-token"))
                .thenReturn(new SpotifyTokens("fresh-access", "fresh-refresh", Instant.now().plusSeconds(3600)));

        String result = service.call(connection, token -> token);

        assertThat(result).isEqualTo("fresh-access");
        assertThat(connection.getAccessToken()).isEqualTo("fresh-access");
        assertThat(connection.getRefreshToken()).isEqualTo("fresh-refresh");
        verify(connectionRepository).save(connection);
    }

    @Test
    void call_retriesOnceAfterForcedRefresh_whenSpotifyRejectsToken() {
        SpotifyConnection connection = connectionExpiringIn(3600);
        when(spotifyClient.refreshToken("refresh-token"))
                .thenReturn(new SpotifyTokens("fresh-access", "fresh-refresh", Instant.now().plusSeconds(3600)));

        java.util.List<String> seenTokens = new java.util.ArrayList<>();
        String result = service.call(connection, token -> {
            seenTokens.add(token);
            if (seenTokens.size() == 1) {
                throw new SpotifyUnauthorizedException("rejected", null);
            }
            return token;
        });

        assertThat(seenTokens).containsExactly("access-token", "fresh-access");
        assertThat(result).isEqualTo("fresh-access");
        verify(spotifyClient, times(1)).refreshToken("refresh-token");
    }
}
