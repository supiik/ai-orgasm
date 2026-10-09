package com.orgasm.backend.spotify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SpotifyClientTest {

    MockRestServiceServer server;
    SpotifyClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new SpotifyClient(builder, "test-client-id", "test-client-secret");
    }

    @Test
    void exchangeCode_postsFormAndParsesTokens() {
        server.expect(requestTo("https://accounts.spotify.com/api/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic dGVzdC1jbGllbnQtaWQ6dGVzdC1jbGllbnQtc2VjcmV0"))
                .andRespond(withSuccess(
                        """
                        {"access_token":"access-1","refresh_token":"refresh-1","expires_in":3600,"token_type":"Bearer"}
                        """,
                        MediaType.APPLICATION_JSON));

        SpotifyTokens tokens = client.exchangeCode("code-1", "http://localhost/callback");

        assertThat(tokens.accessToken()).isEqualTo("access-1");
        assertThat(tokens.refreshToken()).isEqualTo("refresh-1");
        server.verify();
    }

    @Test
    void refreshToken_keepsPreviousRefreshToken_whenSpotifyOmitsANewOne() {
        server.expect(requestTo("https://accounts.spotify.com/api/token"))
                .andRespond(withSuccess(
                        """
                        {"access_token":"access-2","expires_in":3600,"token_type":"Bearer"}
                        """,
                        MediaType.APPLICATION_JSON));

        SpotifyTokens tokens = client.refreshToken("original-refresh");

        assertThat(tokens.accessToken()).isEqualTo("access-2");
        assertThat(tokens.refreshToken()).isEqualTo("original-refresh");
    }

    @Test
    void getCurrentUser_parsesUser() {
        server.expect(requestTo("https://api.spotify.com/v1/me"))
                .andExpect(header("Authorization", "Bearer access-1"))
                .andRespond(withSuccess(
                        """
                        {"id":"spotify-user-1","display_name":"Thom"}
                        """,
                        MediaType.APPLICATION_JSON));

        SpotifyUser user = client.getCurrentUser("access-1");

        assertThat(user.id()).isEqualTo("spotify-user-1");
        assertThat(user.displayName()).isEqualTo("Thom");
    }

    @Test
    void searchTrackUri_returnsFirstMatch() {
        server.expect(requestTo("https://api.spotify.com/v1/search"
                        + "?q=artist%3A%22Radiohead%22%20track%3A%22Creep%22&type=track&limit=1"))
                .andRespond(withSuccess(
                        """
                        {"tracks":{"items":[{"uri":"spotify:track:1"}]}}
                        """,
                        MediaType.APPLICATION_JSON));

        Optional<String> uri = client.searchTrackUri("access-1", "Radiohead", "Creep");

        assertThat(uri).contains("spotify:track:1");
    }

    @Test
    void searchTrackUri_returnsEmpty_whenNoResults() {
        server.expect(requestTo("https://api.spotify.com/v1/search"
                        + "?q=artist%3A%22Unknown%22%20track%3A%22Nothing%22&type=track&limit=1"))
                .andRespond(withSuccess(
                        """
                        {"tracks":{"items":[]}}
                        """,
                        MediaType.APPLICATION_JSON));

        Optional<String> uri = client.searchTrackUri("access-1", "Unknown", "Nothing");

        assertThat(uri).isEmpty();
    }

    @Test
    void createPlaylist_parsesIdAndExternalUrl() {
        server.expect(requestTo("https://api.spotify.com/v1/users/spotify-user-1/playlists"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        """
                        {"id":"pl-1","external_urls":{"spotify":"https://open.spotify.com/playlist/pl-1"}}
                        """,
                        MediaType.APPLICATION_JSON));

        SpotifyPlaylist playlist = client.createPlaylist("access-1", "spotify-user-1", "Mix", "desc");

        assertThat(playlist.id()).isEqualTo("pl-1");
        assertThat(playlist.url()).isEqualTo("https://open.spotify.com/playlist/pl-1");
    }

    @Test
    void replacePlaylistTracks_putsUris() {
        server.expect(requestTo("https://api.spotify.com/v1/playlists/pl-1/tracks"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess());

        client.replacePlaylistTracks("access-1", "pl-1", List.of("spotify:track:1"));

        server.verify();
    }

    @Test
    void unauthorized_isTranslatedToSpotifyUnauthorizedException() {
        server.expect(requestTo("https://api.spotify.com/v1/me"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.getCurrentUser("bad-token"))
                .isInstanceOf(SpotifyUnauthorizedException.class);
    }

    @Test
    void notFound_isTranslatedToSpotifyNotFoundException() {
        server.expect(requestTo("https://api.spotify.com/v1/playlists/missing/tracks"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.replacePlaylistTracks("access-1", "missing", List.of()))
                .isInstanceOf(SpotifyNotFoundException.class);
    }

    @Test
    void serverError_isTranslatedToSpotifyUpstreamException() {
        server.expect(requestTo("https://api.spotify.com/v1/me"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.getCurrentUser("access-1"))
                .isInstanceOf(SpotifyUpstreamException.class)
                .isNotInstanceOf(SpotifyUnauthorizedException.class)
                .isNotInstanceOf(SpotifyNotFoundException.class);
    }
}
