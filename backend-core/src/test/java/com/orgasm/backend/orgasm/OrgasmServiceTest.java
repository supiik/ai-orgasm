package com.orgasm.backend.orgasm;

import com.orgasm.backend.contributor.ContributorRepository;
import com.orgasm.backend.domain.IdGenerator;
import com.orgasm.backend.nomination.Nomination;
import com.orgasm.backend.nomination.NominationMapper;
import com.orgasm.backend.nomination.NominationRepository;
import com.orgasm.backend.nomination.NominationResponse;
import com.orgasm.backend.nomination.NominationStatus;
import com.orgasm.backend.playlist.Playlist;
import com.orgasm.backend.playlist.PlaylistMapper;
import com.orgasm.backend.playlist.PlaylistRepository;
import com.orgasm.backend.playlist.PlaylistResponse;
import com.orgasm.backend.playlist.PlaylistStatus;
import com.orgasm.backend.song.SongRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrgasmServiceTest {

    @Mock PlaylistRepository playlistRepository;
    @Mock ContributorRepository contributorRepository;
    @Mock SongRepository songRepository;
    @Mock NominationRepository nominationRepository;
    @Mock PlaylistMapper playlistMapper;
    @Mock NominationMapper nominationMapper;
    @InjectMocks OrgasmService service;

    static final long PLAYLIST_DB_ID     = 1L;
    static final long CONTRIBUTOR_DB_ID  = 2L;
    static final long SONG_DB_ID         = 3L;
    static final long NOMINATION_DB_ID   = 4L;

    static final String PLAYLIST_ID    = IdGenerator.format("play", PLAYLIST_DB_ID);
    static final String CONTRIBUTOR_ID = IdGenerator.format("cont", CONTRIBUTOR_DB_ID);
    static final String SONG_ID        = IdGenerator.format("song", SONG_DB_ID);
    static final String NOMINATION_ID  = IdGenerator.format("nom",  NOMINATION_DB_ID);

    static final Instant FUTURE = Instant.now().plusSeconds(3600);
    static final Instant PAST   = Instant.now().minusSeconds(3600);

    Playlist newPlaylist() {
        return new Playlist(PLAYLIST_DB_ID, "Mix", null, PlaylistStatus.NEW, null, null);
    }

    Playlist openPlaylist() {
        return new Playlist(PLAYLIST_DB_ID, "Mix", null, PlaylistStatus.OPEN, CONTRIBUTOR_DB_ID, FUTURE);
    }

    PlaylistResponse playlistResponse() {
        return new PlaylistResponse(PLAYLIST_ID, "Mix", null, PlaylistStatus.OPEN, CONTRIBUTOR_ID, FUTURE, 0L, Instant.EPOCH, Instant.EPOCH);
    }

    NominationResponse nominationResponse() {
        return NominationResponse.builder().id(NOMINATION_ID).playlistId(PLAYLIST_ID)
                .songId(SONG_ID).nominatedById(CONTRIBUTOR_ID).status(NominationStatus.PENDING).build();
    }

    // ── openPlaylist ──────────────────────────────────────────────────────────

    @Test
    void openPlaylist_setsLeadContributorAndStatus() {
        var playlist = newPlaylist();
        var saved = openPlaylist();
        var expected = playlistResponse();

        when(playlistRepository.findById(PLAYLIST_DB_ID)).thenReturn(Optional.of(playlist));
        when(contributorRepository.existsById(CONTRIBUTOR_DB_ID)).thenReturn(true);
        when(playlistRepository.save(playlist)).thenReturn(saved);
        when(playlistMapper.toResponse(saved)).thenReturn(expected);

        assertThat(service.openPlaylist(PLAYLIST_ID, new OpenPlaylistRequest(CONTRIBUTOR_ID, FUTURE))).isEqualTo(expected);
        assertThat(playlist.getStatus()).isEqualTo(PlaylistStatus.OPEN);
        assertThat(playlist.getLeadContributorId()).isEqualTo(CONTRIBUTOR_DB_ID);
        assertThat(playlist.getDeadline()).isEqualTo(FUTURE);
    }

    @Test
    void openPlaylist_throwsConflict_whenNotNew() {
        when(playlistRepository.findById(PLAYLIST_DB_ID)).thenReturn(Optional.of(openPlaylist()));

        assertThatThrownBy(() -> service.openPlaylist(PLAYLIST_ID, new OpenPlaylistRequest(CONTRIBUTOR_ID, FUTURE)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NEW");
    }

    @Test
    void openPlaylist_throwsNotFound_whenPlaylistMissing() {
        when(playlistRepository.findById(PLAYLIST_DB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.openPlaylist(PLAYLIST_ID, new OpenPlaylistRequest(CONTRIBUTOR_ID, FUTURE)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void openPlaylist_throwsNotFound_whenContributorMissing() {
        when(playlistRepository.findById(PLAYLIST_DB_ID)).thenReturn(Optional.of(newPlaylist()));
        when(contributorRepository.existsById(CONTRIBUTOR_DB_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.openPlaylist(PLAYLIST_ID, new OpenPlaylistRequest(CONTRIBUTOR_ID, FUTURE)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ── findPlaylistsByContributor ────────────────────────────────────────────

    @Test
    void findPlaylistsByContributor_returnsMappedPage() {
        var playlist = openPlaylist();
        var expected = playlistResponse();
        when(playlistRepository.findByLeadContributorId(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(playlist)));
        when(playlistMapper.toResponse(playlist)).thenReturn(expected);

        var result = service.findPlaylistsByContributor(CONTRIBUTOR_ID, Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(expected);
    }

    // ── nominateSong ──────────────────────────────────────────────────────────

    @Test
    void nominateSong_createsNomination() {
        var nomination = new Nomination(NOMINATION_DB_ID, PLAYLIST_DB_ID, SONG_DB_ID, CONTRIBUTOR_DB_ID, NominationStatus.PENDING);
        var expected = nominationResponse();

        when(playlistRepository.findById(PLAYLIST_DB_ID)).thenReturn(Optional.of(openPlaylist()));
        when(songRepository.existsById(SONG_DB_ID)).thenReturn(true);
        when(contributorRepository.existsById(CONTRIBUTOR_DB_ID)).thenReturn(true);
        when(nominationRepository.existsByPlaylistIdAndSongId(PLAYLIST_DB_ID, SONG_DB_ID)).thenReturn(false);
        when(nominationRepository.save(any(Nomination.class))).thenReturn(nomination);
        when(nominationMapper.toResponse(nomination)).thenReturn(expected);

        assertThat(service.nominateSong(PLAYLIST_ID, new NominateSongRequest(CONTRIBUTOR_ID, SONG_ID))).isEqualTo(expected);
    }

    @Test
    void nominateSong_throwsConflict_whenPlaylistNotOpen() {
        when(playlistRepository.findById(PLAYLIST_DB_ID)).thenReturn(Optional.of(newPlaylist()));

        assertThatThrownBy(() -> service.nominateSong(PLAYLIST_ID, new NominateSongRequest(CONTRIBUTOR_ID, SONG_ID)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not open");
    }

    @Test
    void nominateSong_throwsConflict_whenDeadlinePassed() {
        var playlist = new Playlist(PLAYLIST_DB_ID, "Mix", null, PlaylistStatus.OPEN, CONTRIBUTOR_DB_ID, PAST);
        when(playlistRepository.findById(PLAYLIST_DB_ID)).thenReturn(Optional.of(playlist));

        assertThatThrownBy(() -> service.nominateSong(PLAYLIST_ID, new NominateSongRequest(CONTRIBUTOR_ID, SONG_ID)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("deadline");
    }

    @Test
    void nominateSong_throwsNotFound_whenSongMissing() {
        when(playlistRepository.findById(PLAYLIST_DB_ID)).thenReturn(Optional.of(openPlaylist()));
        when(songRepository.existsById(SONG_DB_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.nominateSong(PLAYLIST_ID, new NominateSongRequest(CONTRIBUTOR_ID, SONG_ID)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void nominateSong_throwsNotFound_whenContributorMissing() {
        when(playlistRepository.findById(PLAYLIST_DB_ID)).thenReturn(Optional.of(openPlaylist()));
        when(songRepository.existsById(SONG_DB_ID)).thenReturn(true);
        when(contributorRepository.existsById(CONTRIBUTOR_DB_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.nominateSong(PLAYLIST_ID, new NominateSongRequest(CONTRIBUTOR_ID, SONG_ID)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void nominateSong_throwsConflict_whenAlreadyNominated() {
        when(playlistRepository.findById(PLAYLIST_DB_ID)).thenReturn(Optional.of(openPlaylist()));
        when(songRepository.existsById(SONG_DB_ID)).thenReturn(true);
        when(contributorRepository.existsById(CONTRIBUTOR_DB_ID)).thenReturn(true);
        when(nominationRepository.existsByPlaylistIdAndSongId(PLAYLIST_DB_ID, SONG_DB_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.nominateSong(PLAYLIST_ID, new NominateSongRequest(CONTRIBUTOR_ID, SONG_ID)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already nominated");
    }

    // ── approveNomination / declineNomination ─────────────────────────────────

    @Test
    void approveNomination_changesStatusToApproved() {
        var nomination = new Nomination(NOMINATION_DB_ID, PLAYLIST_DB_ID, SONG_DB_ID, CONTRIBUTOR_DB_ID, NominationStatus.PENDING);
        var approved = new Nomination(NOMINATION_DB_ID, PLAYLIST_DB_ID, SONG_DB_ID, CONTRIBUTOR_DB_ID, NominationStatus.APPROVED);
        var expected = NominationResponse.builder().id(NOMINATION_ID).status(NominationStatus.APPROVED).build();

        when(nominationRepository.findById(NOMINATION_DB_ID)).thenReturn(Optional.of(nomination));
        when(playlistRepository.findById(PLAYLIST_DB_ID)).thenReturn(Optional.of(openPlaylist()));
        when(nominationRepository.save(nomination)).thenReturn(approved);
        when(nominationMapper.toResponse(approved)).thenReturn(expected);

        assertThat(service.approveNomination(NOMINATION_ID, CONTRIBUTOR_ID)).isEqualTo(expected);
        assertThat(nomination.getStatus()).isEqualTo(NominationStatus.APPROVED);
    }

    @Test
    void declineNomination_changesStatusToDeclined() {
        var nomination = new Nomination(NOMINATION_DB_ID, PLAYLIST_DB_ID, SONG_DB_ID, CONTRIBUTOR_DB_ID, NominationStatus.PENDING);
        var declined = new Nomination(NOMINATION_DB_ID, PLAYLIST_DB_ID, SONG_DB_ID, CONTRIBUTOR_DB_ID, NominationStatus.DECLINED);
        var expected = NominationResponse.builder().id(NOMINATION_ID).status(NominationStatus.DECLINED).build();

        when(nominationRepository.findById(NOMINATION_DB_ID)).thenReturn(Optional.of(nomination));
        when(playlistRepository.findById(PLAYLIST_DB_ID)).thenReturn(Optional.of(openPlaylist()));
        when(nominationRepository.save(nomination)).thenReturn(declined);
        when(nominationMapper.toResponse(declined)).thenReturn(expected);

        assertThat(service.declineNomination(NOMINATION_ID, CONTRIBUTOR_ID)).isEqualTo(expected);
        assertThat(nomination.getStatus()).isEqualTo(NominationStatus.DECLINED);
    }

    @Test
    void reviewNomination_throwsConflict_whenNotPending() {
        var nomination = new Nomination(NOMINATION_DB_ID, PLAYLIST_DB_ID, SONG_DB_ID, CONTRIBUTOR_DB_ID, NominationStatus.APPROVED);
        when(nominationRepository.findById(NOMINATION_DB_ID)).thenReturn(Optional.of(nomination));

        assertThatThrownBy(() -> service.approveNomination(NOMINATION_ID, CONTRIBUTOR_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not pending");
    }

    @Test
    void reviewNomination_throwsConflict_whenNotLeadContributor() {
        var nomination = new Nomination(NOMINATION_DB_ID, PLAYLIST_DB_ID, SONG_DB_ID, CONTRIBUTOR_DB_ID, NominationStatus.PENDING);
        long otherContributorId = 99L;
        String otherId = IdGenerator.format("cont", otherContributorId);

        when(nominationRepository.findById(NOMINATION_DB_ID)).thenReturn(Optional.of(nomination));
        when(playlistRepository.findById(PLAYLIST_DB_ID)).thenReturn(Optional.of(openPlaylist()));

        assertThatThrownBy(() -> service.approveNomination(NOMINATION_ID, otherId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lead contributor");
    }

    @Test
    void reviewNomination_throwsNotFound_whenNominationMissing() {
        when(nominationRepository.findById(NOMINATION_DB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approveNomination(NOMINATION_ID, CONTRIBUTOR_ID))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ── publishPlaylist ───────────────────────────────────────────────────────

    @Test
    void publishPlaylist_setsStatusToPublished() {
        var playlist = new Playlist(PLAYLIST_DB_ID, "Mix", null, PlaylistStatus.OPEN, CONTRIBUTOR_DB_ID, PAST);
        var saved = new Playlist(PLAYLIST_DB_ID, "Mix", null, PlaylistStatus.PUBLISHED, CONTRIBUTOR_DB_ID, PAST);
        var expected = new PlaylistResponse(PLAYLIST_ID, "Mix", null, PlaylistStatus.PUBLISHED, CONTRIBUTOR_ID, PAST, 0L, Instant.EPOCH, Instant.EPOCH);

        when(playlistRepository.findById(PLAYLIST_DB_ID)).thenReturn(Optional.of(playlist));
        when(playlistRepository.save(playlist)).thenReturn(saved);
        when(playlistMapper.toResponse(saved)).thenReturn(expected);

        assertThat(service.publishPlaylist(PLAYLIST_ID, CONTRIBUTOR_ID)).isEqualTo(expected);
        assertThat(playlist.getStatus()).isEqualTo(PlaylistStatus.PUBLISHED);
    }

    @Test
    void publishPlaylist_throwsConflict_whenNotOpen() {
        var playlist = new Playlist(PLAYLIST_DB_ID, "Mix", null, PlaylistStatus.NEW, null, null);
        when(playlistRepository.findById(PLAYLIST_DB_ID)).thenReturn(Optional.of(playlist));

        assertThatThrownBy(() -> service.publishPlaylist(PLAYLIST_ID, CONTRIBUTOR_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("open");
    }

    @Test
    void publishPlaylist_throwsConflict_whenNotLeadContributor() {
        when(playlistRepository.findById(PLAYLIST_DB_ID)).thenReturn(Optional.of(openPlaylist()));

        String otherId = IdGenerator.format("cont", 99L);
        assertThatThrownBy(() -> service.publishPlaylist(PLAYLIST_ID, otherId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lead contributor");
    }

    @Test
    void publishPlaylist_throwsConflict_whenDeadlineNotPassed() {
        when(playlistRepository.findById(PLAYLIST_DB_ID)).thenReturn(Optional.of(openPlaylist()));

        assertThatThrownBy(() -> service.publishPlaylist(PLAYLIST_ID, CONTRIBUTOR_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("deadline");
    }

    // ── findNominations ───────────────────────────────────────────────────────

    @Test
    void findNominations_returnsMappedPage() {
        var nomination = new Nomination(NOMINATION_DB_ID, PLAYLIST_DB_ID, SONG_DB_ID, CONTRIBUTOR_DB_ID, NominationStatus.PENDING);
        var expected = nominationResponse();
        when(nominationRepository.findByPlaylistId(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(nomination)));
        when(nominationMapper.toResponse(nomination)).thenReturn(expected);

        var result = service.findNominations(PLAYLIST_ID, Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(expected);
    }
}
