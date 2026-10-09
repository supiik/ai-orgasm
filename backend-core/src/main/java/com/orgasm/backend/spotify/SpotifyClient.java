package com.orgasm.backend.spotify;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Thin wrapper around Spotify's Accounts + Web API. Every call is synchronous (this app has no
 * reactive stack) and translates transport/HTTP failures into {@link SpotifyUpstreamException}
 * ({@link SpotifyUnauthorizedException} specifically for a 401, so {@link SpotifyTokenService}
 * can refresh and retry once) rather than leaking Spring's {@link RestClientException}. */
@Component
@Slf4j
public class SpotifyClient {

    private static final String ACCOUNTS_TOKEN_URL = "https://accounts.spotify.com/api/token";
    private static final String API_BASE = "https://api.spotify.com/v1";

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;

    public SpotifyClient(
            RestClient.Builder builder,
            @Value("${app.spotify.client-id:}") String clientId,
            @Value("${app.spotify.client-secret:}") String clientSecret) {
        this.restClient = builder.build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @CircuitBreaker(name = "spotify")
    public SpotifyTokens exchangeCode(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        return requestToken(form, null);
    }

    @CircuitBreaker(name = "spotify")
    public SpotifyTokens refreshToken(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        return requestToken(form, refreshToken);
    }

    @CircuitBreaker(name = "spotify")
    public SpotifyUser getCurrentUser(String accessToken) {
        UserResponse response = execute(() -> restClient.get()
                .uri(API_BASE + "/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve()
                .body(UserResponse.class));
        return new SpotifyUser(response.id(), response.displayName());
    }

    @CircuitBreaker(name = "spotify")
    public Optional<String> searchTrackUri(String accessToken, String artist, String title) {
        String query = "artist:\"" + sanitize(artist) + "\" track:\"" + sanitize(title) + "\"";
        SearchResponse response = execute(() -> restClient.get()
                .uri(API_BASE + "/search?q={q}&type=track&limit=1", query)
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve()
                .body(SearchResponse.class));
        if (response == null || response.tracks() == null || response.tracks().items().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(response.tracks().items().getFirst().uri());
    }

    @CircuitBreaker(name = "spotify")
    public SpotifyPlaylist createPlaylist(String accessToken, String spotifyUserId, String name, String description) {
        PlaylistResponse response = execute(() -> restClient.post()
                .uri(API_BASE + "/users/{userId}/playlists", spotifyUserId)
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", name, "description", description, "public", false))
                .retrieve()
                .body(PlaylistResponse.class));
        return new SpotifyPlaylist(response.id(), response.externalUrls().spotify());
    }

    @CircuitBreaker(name = "spotify")
    public void replacePlaylistTracks(String accessToken, String playlistId, List<String> trackUris) {
        execute(() -> restClient.put()
                .uri(API_BASE + "/playlists/{playlistId}/tracks", playlistId)
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("uris", trackUris))
                .retrieve()
                .toBodilessEntity());
    }

    private SpotifyTokens requestToken(MultiValueMap<String, String> form, String previousRefreshToken) {
        TokenResponse response = execute(() -> restClient.post()
                .uri(ACCOUNTS_TOKEN_URL)
                .header(HttpHeaders.AUTHORIZATION, basicAuth())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class));
        String refreshToken = response.refreshToken() != null ? response.refreshToken() : previousRefreshToken;
        return new SpotifyTokens(
                response.accessToken(), refreshToken, Instant.now().plusSeconds(response.expiresIn()));
    }

    private String basicAuth() {
        String credentials = clientId + ":" + clientSecret;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.replace("\"", "");
    }

    private <T> T execute(Supplier<T> call) {
        try {
            return call.get();
        } catch (HttpClientErrorException.Unauthorized ex) {
            throw new SpotifyUnauthorizedException("Spotify rejected the access token", ex);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new SpotifyNotFoundException("Spotify resource not found", ex);
        } catch (RestClientException ex) {
            log.warn("Spotify API call failed", ex);
            throw new SpotifyUpstreamException("Spotify API call failed", ex);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("expires_in") long expiresIn) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record UserResponse(
            @JsonProperty("id") String id,
            @JsonProperty("display_name") String displayName) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SearchResponse(@JsonProperty("tracks") Tracks tracks) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        private record Tracks(@JsonProperty("items") List<Item> items) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        private record Item(@JsonProperty("uri") String uri) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PlaylistResponse(
            @JsonProperty("id") String id,
            @JsonProperty("external_urls") ExternalUrls externalUrls) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        private record ExternalUrls(@JsonProperty("spotify") String spotify) {}
    }
}
