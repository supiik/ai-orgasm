package com.orgasm.backend.spotify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgasm.backend.contributor.Contributor;
import com.orgasm.backend.domain.IdGenerator;
import com.orgasm.backend.nomination.Nomination;
import com.orgasm.backend.nomination.NominationRepository;
import com.orgasm.backend.nomination.NominationStatus;
import com.orgasm.backend.playlist.Playlist;
import com.orgasm.backend.playlist.PlaylistRepository;
import com.orgasm.backend.playlist.PlaylistStatus;
import com.orgasm.backend.song.Song;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SpotifyServiceTest {

    @Mock PlaylistRepository playlistRepository;
    @Mock NominationRepository nominationRepository;
    @Mock SpotifyConnectionRepository connectionRepository;
    @Mock SpotifyClient spotifyClient;
    @Mock SpotifyTokenService spotifyTokenService;
    @Mock SpotifyStateService spotifyStateService;

    SpotifyService service;

    static final long PLAYLIST_DB_ID = 1L;
    static final long LEAD_DB_ID = 2L;
    static final long OTHER_CONTRIBUTOR_DB_ID = 3L;
    static final long SONG_DB_ID = 4L;

    static final String PLAYLIST_ID = IdGenerator.format("play", PLAYLIST_DB_ID);

    @BeforeEach
    void setUp() {
        service = new SpotifyService(
                playlistRepository,
                nominationRepository,
                connectionRepository,
                spotifyClient,
                spotifyTokenService,
                spotifyStateService,
                "test-client-id",
                "http://localhost:5173/spotify/callback");
    }

    static Contributor contributor(long id) {
        var c = new Contributor();
        c.setId(id);
        return c;
    }

    Playlist playlist(PlaylistStatus status, Long leadId) {
        var p = new Playlist(
                PLAYLIST_DB_ID, null, "Mix", null, status,
                leadId == null ? null : contributor(leadId), null, null, null, null, null, 1);
        return p;
    }

    Nomination nomination(String artist, String name) {
        var song = new Song(SONG_DB_ID, null, artist, name, null, null, null);
        return new Nomination(10L, null, null, song, null, NominationStatus.APPROVED);
    }

    SpotifyConnection connection() {
        var c = new SpotifyConnection();
        c.setSpotifyUserId("spotify-user-1");
        return c;
    }

    @SuppressWarnings("unchecked")
    void stubTokenServiceToInvokeAction() {
        when(spotifyTokenService.call(any(), any())).thenAnswer(invocation -> {
            Function<String, Object> action = invocation.getArgument(1);
            return action.apply("access-token");
        });
    }

    // ── getAuthorizeUrl ─────────────────────────────────────────────────────

    @Test
    void getAuthorizeUrl_buildsSpotifyAuthorizeUrl() {
        when(spotifyStateService.mint(PLAYLIST_ID)).thenReturn("signed-state");

        var response = service.getAuthorizeUrl(PLAYLIST_ID);

        assertThat(response.authorizeUrl())
                .startsWith("https://accounts.spotify.com/authorize")
                .contains("client_id=test-client-id")
                .contains("state=signed-state")
                .contains("response_type=code");
    }

    // ── handleCallback ──────────────────────────────────────────────────────

    @Test
    void handleCallback_createsNewConnection_whenNoneExists() {
        Contributor contributor = contributor(LEAD_DB_ID);
        when(spotifyStateService.verify("state-1")).thenReturn(PLAYLIST_ID);
        when(spotifyClient.exchangeCode("code-1", "http://localhost:5173/spotify/callback"))
                .thenReturn(new SpotifyTokens("access", "refresh", java.time.Instant.EPOCH));
        when(spotifyClient.getCurrentUser("access")).thenReturn(new SpotifyUser("spotify-user-1", "Thom"));
        when(connectionRepository.findByContributor_Id(LEAD_DB_ID)).thenReturn(Optional.empty());

        var response = service.handleCallback(contributor, new SpotifyCallbackRequest("code-1", "state-1"));

        assertThat(response.playlistId()).isEqualTo(PLAYLIST_ID);
        var captor = org.mockito.ArgumentCaptor.forClass(SpotifyConnection.class);
        verify(connectionRepository).save(captor.capture());
        assertThat(captor.getValue().getContributor()).isEqualTo(contributor);
        assertThat(captor.getValue().getSpotifyUserId()).isEqualTo("spotify-user-1");
        assertThat(captor.getValue().getAccessToken()).isEqualTo("access");
        assertThat(captor.getValue().getRefreshToken()).isEqualTo("refresh");
    }

    @Test
    void handleCallback_updatesExistingConnection() {
        Contributor contributor = contributor(LEAD_DB_ID);
        SpotifyConnection existing = connection();
        when(spotifyStateService.verify("state-1")).thenReturn(null);
        when(spotifyClient.exchangeCode("code-1", "http://localhost:5173/spotify/callback"))
                .thenReturn(new SpotifyTokens("new-access", "new-refresh", java.time.Instant.EPOCH));
        when(spotifyClient.getCurrentUser("new-access")).thenReturn(new SpotifyUser("spotify-user-1", "Thom"));
        when(connectionRepository.findByContributor_Id(LEAD_DB_ID)).thenReturn(Optional.of(existing));

        var response = service.handleCallback(contributor, new SpotifyCallbackRequest("code-1", "state-1"));

        assertThat(response.playlistId()).isNull();
        verify(connectionRepository).save(existing);
        assertThat(existing.getAccessToken()).isEqualTo("new-access");
    }

    // ── getConnectionStatus ───────────────────────────────────────────────

    @Test
    void getConnectionStatus_true_whenConnectionExists() {
        when(connectionRepository.findByContributor_Id(LEAD_DB_ID)).thenReturn(Optional.of(connection()));
        assertThat(service.getConnectionStatus(contributor(LEAD_DB_ID)).connected()).isTrue();
    }

    @Test
    void getConnectionStatus_false_whenNoConnection() {
        when(connectionRepository.findByContributor_Id(LEAD_DB_ID)).thenReturn(Optional.empty());
        assertThat(service.getConnectionStatus(contributor(LEAD_DB_ID)).connected()).isFalse();
    }

    // ── exportPlaylist: guard rails ──────────────────────────────────────

    @Test
    void exportPlaylist_rejectsNewPlaylist() {
        when(playlistRepository.findById(PLAYLIST_DB_ID)).thenReturn(Optional.of(playlist(PlaylistStatus.NEW, LEAD_DB_ID)));

        assertThatThrownBy(() -> service.exportPlaylist(PLAYLIST_ID, contributor(LEAD_DB_ID)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void exportPlaylist_rejectsOpenPlaylist() {
        when(playlistRepository.findById(PLAYLIST_DB_ID)).thenReturn(Optional.of(playlist(PlaylistStatus.OPEN, LEAD_DB_ID)));

        assertThatThrownBy(() -> service.exportPlaylist(PLAYLIST_ID, contributor(LEAD_DB_ID)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void exportPlaylist_rejectsNonLeadContributor() {
        when(playlistRepository.findById(PLAYLIST_DB_ID)).thenReturn(Optional.of(playlist(PlaylistStatus.GUESSING, LEAD_DB_ID)));

        assertThatThrownBy(() -> service.exportPlaylist(PLAYLIST_ID, contributor(OTHER_CONTRIBUTOR_DB_ID)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void exportPlaylist_rejectsWhenSpotifyNotConnected() {
        when(playlistRepository.findById(PLAYLIST_DB_ID)).thenReturn(Optional.of(playlist(PlaylistStatus.GUESSING, LEAD_DB_ID)));
        when(connectionRepository.findByContributor_Id(LEAD_DB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.exportPlaylist(PLAYLIST_ID, contributor(LEAD_DB_ID)))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── exportPlaylist: matching + create/update ──────────────────────────

    @Test
    void exportPlaylist_createsFreshPlaylist_bestEffortMatching() {
        Playlist playlist = playlist(PlaylistStatus.GUESSING, LEAD_DB_ID);
        when(playlistRepository.findById(PLAYLIST_DB_ID)).thenReturn(Optional.of(playlist));
        when(connectionRepository.findByContributor_Id(LEAD_DB_ID)).thenReturn(Optional.of(connection()));
        when(nominationRepository.findByPlaylist_IdAndStatus(PLAYLIST_DB_ID, NominationStatus.APPROVED))
                .thenReturn(List.of(nomination("Radiohead", "Creep"), nomination("Unknown", "Nothing")));
        stubTokenServiceToInvokeAction();
        when(spotifyClient.searchTrackUri(eq("access-token"), eq("Radiohead"), eq("Creep")))
                .thenReturn(Optional.of("spotify:track:1"));
        when(spotifyClient.searchTrackUri(eq("access-token"), eq("Unknown"), eq("Nothing")))
                .thenReturn(Optional.empty());
        when(spotifyClient.createPlaylist(eq("access-token"), eq("spotify-user-1"), eq("Mix"), any()))
                .thenReturn(new SpotifyPlaylist("new-playlist-id", "https://open.spotify.com/playlist/new-playlist-id"));

        var response = service.exportPlaylist(PLAYLIST_ID, contributor(LEAD_DB_ID));

        assertThat(response.matchedCount()).isEqualTo(1);
        assertThat(response.skippedSongs()).containsExactly("Unknown - Nothing");
        assertThat(response.spotifyPlaylistId()).isEqualTo("new-playlist-id");
        verify(spotifyClient).replacePlaylistTracks("access-token", "new-playlist-id", List.of("spotify:track:1"));
        assertThat(playlist.getSpotifyPlaylistId()).isEqualTo("new-playlist-id");
        assertThat(playlist.getSpotifyPlaylistUrl()).isEqualTo("https://open.spotify.com/playlist/new-playlist-id");
        verify(playlistRepository).save(playlist);
    }

    @Test
    void exportPlaylist_updatesExistingSpotifyPlaylist_idempotently() {
        Playlist playlist = playlist(PlaylistStatus.PUBLISHED, LEAD_DB_ID);
        playlist.setSpotifyPlaylistId("existing-id");
        playlist.setSpotifyPlaylistUrl("https://open.spotify.com/playlist/existing-id");
        when(playlistRepository.findById(PLAYLIST_DB_ID)).thenReturn(Optional.of(playlist));
        when(connectionRepository.findByContributor_Id(LEAD_DB_ID)).thenReturn(Optional.of(connection()));
        when(nominationRepository.findByPlaylist_IdAndStatus(PLAYLIST_DB_ID, NominationStatus.APPROVED))
                .thenReturn(List.of(nomination("Radiohead", "Creep")));
        stubTokenServiceToInvokeAction();
        when(spotifyClient.searchTrackUri(eq("access-token"), eq("Radiohead"), eq("Creep")))
                .thenReturn(Optional.of("spotify:track:1"));

        var response = service.exportPlaylist(PLAYLIST_ID, contributor(LEAD_DB_ID));

        assertThat(response.spotifyPlaylistId()).isEqualTo("existing-id");
        verify(spotifyClient).replacePlaylistTracks("access-token", "existing-id", List.of("spotify:track:1"));
        verify(spotifyClient, never()).createPlaylist(any(), any(), any(), any());
    }

    @Test
    void exportPlaylist_fallsBackToCreatingFresh_whenStoredPlaylistIsGone() {
        Playlist playlist = playlist(PlaylistStatus.PUBLISHED, LEAD_DB_ID);
        playlist.setSpotifyPlaylistId("deleted-id");
        playlist.setSpotifyPlaylistUrl("https://open.spotify.com/playlist/deleted-id");
        when(playlistRepository.findById(PLAYLIST_DB_ID)).thenReturn(Optional.of(playlist));
        when(connectionRepository.findByContributor_Id(LEAD_DB_ID)).thenReturn(Optional.of(connection()));
        when(nominationRepository.findByPlaylist_IdAndStatus(PLAYLIST_DB_ID, NominationStatus.APPROVED))
                .thenReturn(List.of());
        stubTokenServiceToInvokeAction();
        doThrow(new SpotifyNotFoundException("gone", null))
                .when(spotifyClient).replacePlaylistTracks(eq("access-token"), eq("deleted-id"), any());
        when(spotifyClient.createPlaylist(eq("access-token"), eq("spotify-user-1"), eq("Mix"), any()))
                .thenReturn(new SpotifyPlaylist("fresh-id", "https://open.spotify.com/playlist/fresh-id"));

        var response = service.exportPlaylist(PLAYLIST_ID, contributor(LEAD_DB_ID));

        assertThat(response.spotifyPlaylistId()).isEqualTo("fresh-id");
        assertThat(response.matchedCount()).isZero();
    }
}
