package com.orgasm.backend.orgasm;

import com.orgasm.backend.contributor.Contributor;
import com.orgasm.backend.contributor.ContributorRepository;
import com.orgasm.backend.domain.IdGenerator;
import com.orgasm.backend.guessing.Guess;
import com.orgasm.backend.guessing.GuessRepository;
import com.orgasm.backend.guessing.GuessSubmissionRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrgasmServiceTest {

    @Mock PlaylistRepository playlistRepository;
    @Mock ContributorRepository contributorRepository;
    @Mock SongRepository songRepository;
    @Mock NominationRepository nominationRepository;
    @Mock GuessSubmissionRepository guessSubmissionRepository;
    @Mock GuessRepository guessRepository;
    @Mock PlaylistMapper playlistMapper;
    @Mock NominationMapper nominationMapper;
    @InjectMocks OrgasmService service;

    static final long PLAYLIST_DB_ID    = 1L;
    static final long CONTRIBUTOR_DB_ID = 2L;
    static final long SONG_DB_ID        = 3L;
    static final long NOMINATION_DB_ID  = 4L;

    static final String PLAYLIST_ID    = IdGenerator.format("play", PLAYLIST_DB_ID);
    static final String CONTRIBUTOR_ID = IdGenerator.format("cont", CONTRIBUTOR_DB_ID);
    static final String SONG_ID        = IdGenerator.format("song", SONG_DB_ID);
    static final String NOMINATION_ID  = IdGenerator.format("nom",  NOMINATION_DB_ID);

    static final Instant FUTURE = Instant.now().plusSeconds(3600);
    static final Instant PAST   = Instant.now().minusSeconds(3600);

    static Contributor leadContributor() {
        var c = new Contributor();
        c.setId(CONTRIBUTOR_DB_ID);
        return c;
    }

    Playlist newPlaylist() {
        return new Playlist(PLAYLIST_DB_ID, null, "Mix", null, PlaylistStatus.NEW, null, null, null);
    }

    Playlist openPlaylist() {
        return new Playlist(PLAYLIST_DB_ID, null, "Mix", null, PlaylistStatus.OPEN, leadContributor(), FUTURE, null);
    }

    Playlist openPlaylistPastDeadline() {
        return new Playlist(PLAYLIST_DB_ID, null, "Mix", null, PlaylistStatus.OPEN, leadContributor(), PAST, null);
    }

    Playlist guessingPlaylist() {
        return new Playlist(PLAYLIST_DB_ID, null, "Mix", null, PlaylistStatus.GUESSING, leadContributor(), PAST, null);
    }

    PlaylistResponse playlistResponse() {
        return new PlaylistResponse(PLAYLIST_ID, "Mix", null, PlaylistStatus.OPEN, CONTRIBUTOR_ID, null, null, FUTURE, null, 0L, Instant.EPOCH, Instant.EPOCH);
    }

    NominationResponse nominationResponse() {
        return NominationResponse.builder().id(NOMINATION_ID).playlistId(PLAYLIST_ID)
                .songId(SONG_ID).nominatedById(CONTRIBUTOR_ID).status(NominationStatus.PENDING).build();
    }

    Nomination pendingNomination() {
        var n = new Nomination();
        n.setId(NOMINATION_DB_ID);
        n.setPlaylist(openPlaylist());
        n.setStatus(NominationStatus.PENDING);
        return n;
    }

    // ── openPlaylist ──────────────────────────────────────────────────────────

    @Test
    void openPlaylist_setsLeadContributorAndStatus() {
        var playlist = newPlaylist();
        var freshPlaylist = openPlaylist();
        var expected = playlistResponse();

        when(playlistRepository.findById(PLAYLIST_DB_ID))
                .thenReturn(Optional.of(playlist))
                .thenReturn(Optional.of(freshPlaylist));
        when(contributorRepository.existsById(CONTRIBUTOR_DB_ID)).thenReturn(true);
        when(playlistMapper.toResponse(any(Playlist.class))).thenReturn(expected);

        assertThat(service.openPlaylist(PLAYLIST_ID, new OpenPlaylistRequest(CONTRIBUTOR_ID, FUTURE))).isEqualTo(expected);
        assertThat(playlist.getStatus()).isEqualTo(PlaylistStatus.OPEN);
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
        when(playlistRepository.findByLeadContributor_Id(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(playlist)));
        when(playlistMapper.toResponse(playlist)).thenReturn(expected);

        var result = service.findPlaylistsByContributor(CONTRIBUTOR_ID, Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(expected);
    }

    // ── nominateSong ──────────────────────────────────────────────────────────

    @Test
    void nominateSong_createsNomination() {
        var expected = nominationResponse();

        when(playlistRepository.findById(PLAYLIST_DB_ID)).thenReturn(Optional.of(openPlaylist()));
        when(songRepository.existsById(SONG_DB_ID)).thenReturn(true);
        when(contributorRepository.existsById(CONTRIBUTOR_DB_ID)).thenReturn(true);
        when(nominationRepository.existsByPlaylist_IdAndSong_Id(PLAYLIST_DB_ID, SONG_DB_ID)).thenReturn(false);
        when(nominationRepository.save(any(Nomination.class))).thenReturn(pendingNomination());
        when(nominationMapper.toResponse(any())).thenReturn(expected);

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
        var playlist = new Playlist(PLAYLIST_DB_ID, null, "Mix", null, PlaylistStatus.OPEN, leadContributor(), PAST, null);
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
        when(nominationRepository.existsByPlaylist_IdAndSong_Id(PLAYLIST_DB_ID, SONG_DB_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.nominateSong(PLAYLIST_ID, new NominateSongRequest(CONTRIBUTOR_ID, SONG_ID)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already nominated");
    }

    // ── approveNomination / declineNomination ─────────────────────────────────

    @Test
    void approveNomination_changesStatusToApproved() {
        var nomination = pendingNomination();
        var approved = pendingNomination();
        approved.setStatus(NominationStatus.APPROVED);
        var expected = NominationResponse.builder().id(NOMINATION_ID).status(NominationStatus.APPROVED).build();

        when(nominationRepository.findById(NOMINATION_DB_ID)).thenReturn(Optional.of(nomination));
        when(nominationRepository.save(nomination)).thenReturn(approved);
        when(nominationMapper.toResponse(approved)).thenReturn(expected);

        assertThat(service.approveNomination(NOMINATION_ID, CONTRIBUTOR_ID)).isEqualTo(expected);
        assertThat(nomination.getStatus()).isEqualTo(NominationStatus.APPROVED);
    }

    @Test
    void declineNomination_changesStatusToDeclined() {
        var nomination = pendingNomination();
        var declined = pendingNomination();
        declined.setStatus(NominationStatus.DECLINED);
        var expected = NominationResponse.builder().id(NOMINATION_ID).status(NominationStatus.DECLINED).build();

        when(nominationRepository.findById(NOMINATION_DB_ID)).thenReturn(Optional.of(nomination));
        when(nominationRepository.save(nomination)).thenReturn(declined);
        when(nominationMapper.toResponse(declined)).thenReturn(expected);

        assertThat(service.declineNomination(NOMINATION_ID, CONTRIBUTOR_ID)).isEqualTo(expected);
        assertThat(nomination.getStatus()).isEqualTo(NominationStatus.DECLINED);
    }

    @Test
    void reviewNomination_throwsConflict_whenNotPending() {
        var nomination = pendingNomination();
        nomination.setStatus(NominationStatus.APPROVED);
        when(nominationRepository.findById(NOMINATION_DB_ID)).thenReturn(Optional.of(nomination));

        assertThatThrownBy(() -> service.approveNomination(NOMINATION_ID, CONTRIBUTOR_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not pending");
    }

    @Test
    void reviewNomination_throwsConflict_whenNotLeadContributor() {
        var nomination = pendingNomination();
        String otherId = IdGenerator.format("cont", 99L);

        when(nominationRepository.findById(NOMINATION_DB_ID)).thenReturn(Optional.of(nomination));

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

    // ── startGuessing ─────────────────────────────────────────────────────────

    @Test
    void startGuessing_setsStatusToGuessing_whenDeadlinePassed() {
        var playlist = openPlaylistPastDeadline();
        var saved = new Playlist(PLAYLIST_DB_ID, null, "Mix", null, PlaylistStatus.GUESSING, leadContributor(), PAST, null);
        var expected = new PlaylistResponse(PLAYLIST_ID, "Mix", null, PlaylistStatus.GUESSING, CONTRIBUTOR_ID, null, null, PAST, null, 0L, Instant.EPOCH, Instant.EPOCH);

        when(playlistRepository.findById(PLAYLIST_DB_ID)).thenReturn(Optional.of(playlist));
        when(playlistRepository.save(playlist)).thenReturn(saved);
        when(playlistMapper.toResponse(saved)).thenReturn(expected);

        assertThat(service.startGuessing(PLAYLIST_ID, CONTRIBUTOR_ID)).isEqualTo(expected);
        assertThat(playlist.getStatus()).isEqualTo(PlaylistStatus.GUESSING);
        verify(nominationRepository).declinePendingByPlaylistId(PLAYLIST_DB_ID);
    }

    @Test
    void startGuessing_setsStatusToGuessing_whenNoPendingNominations() {
        var playlist = openPlaylist();
        var saved = new Playlist(PLAYLIST_DB_ID, null, "Mix", null, PlaylistStatus.GUESSING, leadContributor(), FUTURE, null);
        var expected = new PlaylistResponse(PLAYLIST_ID, "Mix", null, PlaylistStatus.GUESSING, CONTRIBUTOR_ID, null, null, FUTURE, null, 0L, Instant.EPOCH, Instant.EPOCH);

        when(playlistRepository.findById(PLAYLIST_DB_ID)).thenReturn(Optional.of(playlist));
        when(nominationRepository.findByPlaylist_IdAndStatus(PLAYLIST_DB_ID, NominationStatus.PENDING)).thenReturn(List.of());
        when(playlistRepository.save(playlist)).thenReturn(saved);
        when(playlistMapper.toResponse(saved)).thenReturn(expected);

        assertThat(service.startGuessing(PLAYLIST_ID, CONTRIBUTOR_ID)).isEqualTo(expected);
        verify(nominationRepository).declinePendingByPlaylistId(PLAYLIST_DB_ID);
    }

    @Test
    void startGuessing_throwsConflict_whenNotOpen() {
        when(playlistRepository.findById(PLAYLIST_DB_ID)).thenReturn(Optional.of(newPlaylist()));

        assertThatThrownBy(() -> service.startGuessing(PLAYLIST_ID, CONTRIBUTOR_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("open");
    }

    @Test
    void startGuessing_throwsConflict_whenNotLeadContributor() {
        when(playlistRepository.findById(PLAYLIST_DB_ID)).thenReturn(Optional.of(openPlaylistPastDeadline()));

        String otherId = IdGenerator.format("cont", 99L);
        assertThatThrownBy(() -> service.startGuessing(PLAYLIST_ID, otherId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lead contributor");
    }

    @Test
    void startGuessing_throwsConflict_whenDeadlineNotPassedAndHasPending() {
        var nomination = pendingNomination();
        when(playlistRepository.findById(PLAYLIST_DB_ID)).thenReturn(Optional.of(openPlaylist()));
        when(nominationRepository.findByPlaylist_IdAndStatus(PLAYLIST_DB_ID, NominationStatus.PENDING)).thenReturn(List.of(nomination));

        assertThatThrownBy(() -> service.startGuessing(PLAYLIST_ID, CONTRIBUTOR_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pending");
    }

    // ── publishPlaylist ───────────────────────────────────────────────────────

    @Test
    void publishPlaylist_setsStatusToPublished() {
        var playlist = guessingPlaylist();
        var saved = new Playlist(PLAYLIST_DB_ID, null, "Mix", null, PlaylistStatus.PUBLISHED, leadContributor(), PAST, null);
        var expected = new PlaylistResponse(PLAYLIST_ID, "Mix", null, PlaylistStatus.PUBLISHED, CONTRIBUTOR_ID, null, null, PAST, null, 0L, Instant.EPOCH, Instant.EPOCH);

        when(playlistRepository.findById(PLAYLIST_DB_ID)).thenReturn(Optional.of(playlist));
        when(playlistRepository.save(playlist)).thenReturn(saved);
        when(playlistMapper.toResponse(saved)).thenReturn(expected);

        assertThat(service.publishPlaylist(PLAYLIST_ID, CONTRIBUTOR_ID)).isEqualTo(expected);
        assertThat(playlist.getStatus()).isEqualTo(PlaylistStatus.PUBLISHED);
    }

    @Test
    void publishPlaylist_throwsConflict_whenNotGuessing() {
        when(playlistRepository.findById(PLAYLIST_DB_ID)).thenReturn(Optional.of(openPlaylist()));

        assertThatThrownBy(() -> service.publishPlaylist(PLAYLIST_ID, CONTRIBUTOR_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("guessing");
    }

    @Test
    void publishPlaylist_throwsConflict_whenNotLeadContributor() {
        when(playlistRepository.findById(PLAYLIST_DB_ID)).thenReturn(Optional.of(guessingPlaylist()));

        String otherId = IdGenerator.format("cont", 99L);
        assertThatThrownBy(() -> service.publishPlaylist(PLAYLIST_ID, otherId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lead contributor");
    }

    // ── getGuesses ────────────────────────────────────────────────────────────

    @Test
    void getGuesses_returnsMappedList() {
        var guess = new Guess();
        guess.setNomination(pendingNomination());
        var guesser = leadContributor();
        guess.setGuesser(guesser);
        var guessed = new Contributor(); guessed.setId(99L);
        guess.setGuessedContributor(guessed);
        when(guessRepository.findByPlaylist_Id(PLAYLIST_DB_ID)).thenReturn(List.of(guess));

        var result = service.getGuesses(PLAYLIST_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).nominationId()).isEqualTo(NOMINATION_ID);
        assertThat(result.get(0).guesserId()).isEqualTo(CONTRIBUTOR_ID);
        assertThat(result.get(0).guessedContributorId()).isEqualTo(IdGenerator.format("cont", 99L));
    }

    // ── findNominations ───────────────────────────────────────────────────────

    @Test
    void findNominations_returnsMappedPage() {
        var nomination = pendingNomination();
        var expected = nominationResponse();
        when(nominationRepository.findByPlaylist_Id(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(nomination)));
        when(nominationMapper.toResponse(nomination)).thenReturn(expected);

        var result = service.findNominations(PLAYLIST_ID, Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(expected);
    }
}
