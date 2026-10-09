package com.orgasm.backend.spotify;

import com.orgasm.backend.contributor.Contributor;
import com.orgasm.backend.domain.IdGenerator;
import com.orgasm.backend.nomination.Nomination;
import com.orgasm.backend.nomination.NominationRepository;
import com.orgasm.backend.nomination.NominationStatus;
import com.orgasm.backend.playlist.Playlist;
import com.orgasm.backend.playlist.PlaylistRepository;
import com.orgasm.backend.playlist.PlaylistStatus;
import com.orgasm.backend.song.Song;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

/** Orchestrates the "connect a Spotify account, export a playlist's approved nominations"
 * feature. Mirrors {@code OrgasmService}'s conventions (an {@code IllegalStateException} for
 * workflow/permission violations, mapped to 409 by {@code GlobalExceptionHandler}; contributor
 * is always a resolved {@link Contributor}, never a body-supplied id — see
 * {@code SpotifyController}). Uses an explicit constructor (not Lombok's
 * {@code @RequiredArgsConstructor}) so the two {@code @Value}-sourced strings are
 * constructor-injected like the collaborator beans, rather than needing field injection that
 * plain Mockito unit tests can't populate. */
@Service
@Transactional("appTransactionManager")
@Slf4j
public class SpotifyService {

    private static final String AUTHORIZE_URL = "https://accounts.spotify.com/authorize";
    private static final String SCOPES = "playlist-modify-public playlist-modify-private";

    private final PlaylistRepository playlistRepository;
    private final NominationRepository nominationRepository;
    private final SpotifyConnectionRepository connectionRepository;
    private final SpotifyClient spotifyClient;
    private final SpotifyTokenService spotifyTokenService;
    private final SpotifyStateService spotifyStateService;
    private final String clientId;
    private final String redirectUri;

    public SpotifyService(
            PlaylistRepository playlistRepository,
            NominationRepository nominationRepository,
            SpotifyConnectionRepository connectionRepository,
            SpotifyClient spotifyClient,
            SpotifyTokenService spotifyTokenService,
            SpotifyStateService spotifyStateService,
            @Value("${app.spotify.client-id:}") String clientId,
            @Value("${app.spotify.redirect-uri:}") String redirectUri) {
        this.playlistRepository = playlistRepository;
        this.nominationRepository = nominationRepository;
        this.connectionRepository = connectionRepository;
        this.spotifyClient = spotifyClient;
        this.spotifyTokenService = spotifyTokenService;
        this.spotifyStateService = spotifyStateService;
        this.clientId = clientId;
        this.redirectUri = redirectUri;
    }

    @Transactional(readOnly = true)
    public SpotifyAuthorizeResponse getAuthorizeUrl(String playlistId) {
        String state = spotifyStateService.mint(playlistId);
        String url = UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
                .queryParam("client_id", clientId)
                .queryParam("response_type", "code")
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", SCOPES)
                .queryParam("state", state)
                .build()
                .toUriString();
        return SpotifyAuthorizeResponse.builder().authorizeUrl(url).build();
    }

    public SpotifyCallbackResponse handleCallback(Contributor contributor, SpotifyCallbackRequest request) {
        String playlistId = spotifyStateService.verify(request.state());
        SpotifyTokens tokens = spotifyClient.exchangeCode(request.code(), redirectUri);
        SpotifyUser spotifyUser = spotifyClient.getCurrentUser(tokens.accessToken());

        SpotifyConnection connection = connectionRepository.findByContributor_Id(contributor.getId())
                .orElseGet(SpotifyConnection::new);
        connection.setContributor(contributor);
        connection.setSpotifyUserId(spotifyUser.id());
        connection.setAccessToken(tokens.accessToken());
        connection.setRefreshToken(tokens.refreshToken());
        connection.setExpiresAt(tokens.expiresAt());
        connectionRepository.save(connection);

        return SpotifyCallbackResponse.builder().playlistId(playlistId).build();
    }

    @CircuitBreaker(name = "db")
    @Transactional(readOnly = true)
    public SpotifyConnectionStatusResponse getConnectionStatus(Contributor contributor) {
        boolean connected = connectionRepository.findByContributor_Id(contributor.getId()).isPresent();
        return SpotifyConnectionStatusResponse.builder().connected(connected).build();
    }

    public SpotifyExportResponse exportPlaylist(String playlistId, Contributor contributor) {
        Playlist playlist = requirePlaylist(playlistId);
        if (playlist.getStatus() == PlaylistStatus.NEW || playlist.getStatus() == PlaylistStatus.OPEN) {
            throw new IllegalStateException(
                    "Nominations are not final yet, playlist status is " + playlist.getStatus());
        }
        if (playlist.getLeadContributor() == null
                || !Objects.equals(playlist.getLeadContributor().getId(), contributor.getId())) {
            throw new IllegalStateException("Only the lead contributor can export a playlist to Spotify");
        }
        SpotifyConnection connection = connectionRepository.findByContributor_Id(contributor.getId())
                .orElseThrow(() -> new IllegalStateException("Spotify is not connected"));

        List<Nomination> approved = nominationRepository.findByPlaylist_IdAndStatus(
                playlist.getId(), NominationStatus.APPROVED);

        List<String> trackUris = new ArrayList<>();
        List<String> skippedSongs = new ArrayList<>();
        for (Nomination nomination : approved) {
            Song song = nomination.getSong();
            Optional<String> trackUri = spotifyTokenService.call(
                    connection, token -> spotifyClient.searchTrackUri(token, song.getArtist(), song.getName()));
            if (trackUri.isPresent()) {
                trackUris.add(trackUri.get());
            } else {
                skippedSongs.add(song.getArtist() + " - " + song.getName());
            }
        }

        SpotifyPlaylist spotifyPlaylist = createOrUpdatePlaylist(connection, playlist, trackUris);

        playlist.setSpotifyPlaylistId(spotifyPlaylist.id());
        playlist.setSpotifyPlaylistUrl(spotifyPlaylist.url());
        playlistRepository.save(playlist);

        return SpotifyExportResponse.builder()
                .spotifyPlaylistId(spotifyPlaylist.id())
                .spotifyPlaylistUrl(spotifyPlaylist.url())
                .matchedCount(trackUris.size())
                .skippedSongs(skippedSongs)
                .build();
    }

    private SpotifyPlaylist createOrUpdatePlaylist(SpotifyConnection connection, Playlist playlist, List<String> trackUris) {
        String existingId = playlist.getSpotifyPlaylistId();
        if (existingId != null) {
            try {
                spotifyTokenService.call(connection, token -> {
                    spotifyClient.replacePlaylistTracks(token, existingId, trackUris);
                    return null;
                });
                return new SpotifyPlaylist(existingId, playlist.getSpotifyPlaylistUrl());
            } catch (SpotifyNotFoundException ex) {
                log.info("Stored Spotify playlist {} no longer exists, creating a new one", existingId);
            }
        }
        SpotifyPlaylist created = spotifyTokenService.call(connection, token -> spotifyClient.createPlaylist(
                token, connection.getSpotifyUserId(), playlist.getName(), playlistDescription(playlist)));
        if (!trackUris.isEmpty()) {
            spotifyTokenService.call(connection, token -> {
                spotifyClient.replacePlaylistTracks(token, created.id(), trackUris);
                return null;
            });
        }
        return created;
    }

    private static String playlistDescription(Playlist playlist) {
        return "Created by Orgasm from playlist \"" + playlist.getName() + "\"";
    }

    private Playlist requirePlaylist(String playlistId) {
        return playlistRepository.findById(IdGenerator.parse(playlistId))
                .orElseThrow(() -> new EntityNotFoundException("Playlist not found: " + playlistId));
    }
}
