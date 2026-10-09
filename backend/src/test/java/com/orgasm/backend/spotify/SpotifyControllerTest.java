package com.orgasm.backend.spotify;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgasm.backend.config.GlobalExceptionHandler;
import com.orgasm.backend.config.VersionTestSupport;
import com.orgasm.backend.contributor.Contributor;
import com.orgasm.backend.contributor.ContributorRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Every endpoint resolves the contributor from {@code SecurityContextHolder}'s JWT (mirroring
 * how {@code ContributorController.me()} does it, and how {@code RequestLoggingFilterTest} sets
 * up an authenticated {@link JwtAuthenticationToken} for a standalone MockMvc test) rather than
 * from a request body/param, so every test authenticates as "alice@example.com" first. */
class SpotifyControllerTest {

    MockMvc mvc;
    SpotifyService spotifyService = mock(SpotifyService.class);
    ContributorRepository contributorRepository = mock(ContributorRepository.class);
    ObjectMapper objectMapper = new ObjectMapper();

    static final Contributor CONTRIBUTOR = new Contributor(2L, null, "Alice", "alice@example.com", null);

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders
                .standaloneSetup(new SpotifyController(spotifyService, contributorRepository))
                .setApiVersionStrategy(VersionTestSupport.pathVersionStrategy())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                Jwt.withTokenValue("t").header("alg", "none")
                        .subject("sub-1")
                        .claim("email", "alice@example.com")
                        .build()));
        when(contributorRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(CONTRIBUTOR));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void connect_resolvesContributorFromJwt_andReturnsAuthorizeUrl() throws Exception {
        when(spotifyService.getAuthorizeUrl("play-0001"))
                .thenReturn(SpotifyAuthorizeResponse.builder().authorizeUrl("https://accounts.spotify.com/authorize?x=1").build());

        mvc.perform(get("/api/v1/spotify/connect").param("playlistId", "play-0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorizeUrl").value("https://accounts.spotify.com/authorize?x=1"));

        verify(spotifyService).getAuthorizeUrl("play-0001");
    }

    @Test
    void status_returnsConnectionStatus() throws Exception {
        when(spotifyService.getConnectionStatus(CONTRIBUTOR))
                .thenReturn(SpotifyConnectionStatusResponse.builder().connected(true).build());

        mvc.perform(get("/api/v1/spotify/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true));
    }

    @Test
    void callback_returnsRedirectPlaylistId() throws Exception {
        when(spotifyService.handleCallback(eq(CONTRIBUTOR), any(SpotifyCallbackRequest.class)))
                .thenReturn(SpotifyCallbackResponse.builder().playlistId("play-0001").build());

        mvc.perform(post("/api/v1/spotify/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SpotifyCallbackRequest("code-1", "state-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playlistId").value("play-0001"));
    }

    @Test
    void export_delegatesToService() throws Exception {
        when(spotifyService.exportPlaylist("play-0001", CONTRIBUTOR))
                .thenReturn(SpotifyExportResponse.builder()
                        .spotifyPlaylistId("sp-1")
                        .spotifyPlaylistUrl("https://open.spotify.com/playlist/sp-1")
                        .matchedCount(2)
                        .skippedSongs(List.of())
                        .build());

        mvc.perform(post("/api/v1/spotify/playlists/play-0001/export"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spotifyPlaylistUrl").value("https://open.spotify.com/playlist/sp-1"))
                .andExpect(jsonPath("$.matchedCount").value(2));
    }

    @Test
    void export_returns404_whenNoContributorLinkedToEmail() throws Exception {
        when(contributorRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());

        mvc.perform(post("/api/v1/spotify/playlists/play-0001/export"))
                .andExpect(status().isNotFound());
    }
}
