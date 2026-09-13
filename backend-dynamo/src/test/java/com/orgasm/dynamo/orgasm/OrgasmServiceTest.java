package com.orgasm.dynamo.orgasm;

import com.orgasm.dynamo.contributor.ContributorDynamoRepository;
import com.orgasm.dynamo.contributor.ContributorItem;
import com.orgasm.dynamo.domain.IdGenerator;
import com.orgasm.dynamo.guessing.GuessDynamoRepository;
import com.orgasm.dynamo.guessing.GuessItem;
import com.orgasm.dynamo.guessing.GuessSubmissionDynamoRepository;
import com.orgasm.dynamo.nomination.NominationDynamoRepository;
import com.orgasm.dynamo.nomination.NominationItem;
import com.orgasm.dynamo.nomination.NominationStatus;
import com.orgasm.dynamo.playlist.PlaylistDynamoRepository;
import com.orgasm.dynamo.playlist.PlaylistItem;
import com.orgasm.dynamo.playlist.PlaylistMapper;
import com.orgasm.dynamo.playlist.PlaylistResponse;
import com.orgasm.dynamo.playlist.PlaylistStatus;
import com.orgasm.dynamo.ranking.PlaylistRankingDynamoRepository;
import com.orgasm.dynamo.ranking.PlaylistRankingItem;
import com.orgasm.dynamo.rating.SongRatingDynamoRepository;
import com.orgasm.dynamo.song.SongDynamoRepository;
import com.orgasm.dynamo.song.SongItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrgasmServiceTest {

    @Mock PlaylistDynamoRepository playlistRepository;
    @Mock PlaylistMapper playlistMapper;
    @Mock ContributorDynamoRepository contributorRepository;
    @Mock SongDynamoRepository songRepository;
    @Mock NominationDynamoRepository nominationRepository;
    @Mock GuessDynamoRepository guessRepository;
    @Mock GuessSubmissionDynamoRepository guessSubmissionRepository;
    @Mock SongRatingDynamoRepository songRatingRepository;
    @Mock PlaylistRankingDynamoRepository playlistRankingRepository;
    @InjectMocks OrgasmService service;

    static final long TENANT_ID = 1L;

    private static PlaylistItem playlist(long id, PlaylistStatus status) {
        PlaylistItem item = new PlaylistItem();
        item.setId(id);
        item.setTenantId(TENANT_ID);
        item.setName("My Mix");
        item.setStatus(status.name());
        return item;
    }

    private static ContributorItem contributor(long id) {
        ContributorItem item = new ContributorItem();
        item.setId(id);
        item.setTenantId(TENANT_ID);
        item.setName("Ada");
        return item;
    }

    private static SongItem song(long id) {
        SongItem item = new SongItem();
        item.setId(id);
        item.setTenantId(TENANT_ID);
        item.setName("A Song");
        return item;
    }

    private static NominationItem nomination(long id, long playlistId, long songId, long nominatedById, String status) {
        NominationItem item = new NominationItem();
        item.setId(id);
        item.setTenantId(TENANT_ID);
        item.setPlaylistId(playlistId);
        item.setSongId(songId);
        item.setNominatedById(nominatedById);
        item.setStatus(status);
        return item;
    }

    private static String id(long dbId) {
        return IdGenerator.format("play", dbId);
    }

    // ---- openPlaylist ----

    @Test
    void openPlaylist_succeeds_whenNew() {
        var playlist = playlist(1L, PlaylistStatus.NEW);
        when(playlistRepository.findById(TENANT_ID, 1L)).thenReturn(Optional.of(playlist));
        when(contributorRepository.findById(TENANT_ID, 2L)).thenReturn(Optional.of(contributor(2L)));
        when(playlistRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(playlistMapper.toResponse(any())).thenReturn(PlaylistResponse.builder().id("play-1").build());

        var deadline = Instant.now().plusSeconds(60);
        service.openPlaylist(id(1L), OpenPlaylistRequest.builder()
                .contributorId(IdGenerator.format("cont", 2L))
                .deadline(deadline)
                .build());

        var captor = ArgumentCaptor.forClass(PlaylistItem.class);
        verify(playlistRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PlaylistStatus.OPEN.name());
        assertThat(captor.getValue().getLeadContributorId()).isEqualTo(2L);
        assertThat(captor.getValue().getDeadline()).isEqualTo(deadline);
    }

    @Test
    void openPlaylist_throws_whenNotNew() {
        when(playlistRepository.findById(TENANT_ID, 1L)).thenReturn(Optional.of(playlist(1L, PlaylistStatus.OPEN)));

        assertThatThrownBy(() -> service.openPlaylist(id(1L),
                OpenPlaylistRequest.builder().contributorId(IdGenerator.format("cont", 2L)).deadline(Instant.now()).build()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void openPlaylist_throws_whenContributorMissing() {
        when(playlistRepository.findById(TENANT_ID, 1L)).thenReturn(Optional.of(playlist(1L, PlaylistStatus.NEW)));
        when(contributorRepository.findById(TENANT_ID, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.openPlaylist(id(1L),
                OpenPlaylistRequest.builder().contributorId(IdGenerator.format("cont", 2L)).deadline(Instant.now()).build()))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    // ---- nominateSong ----

    @Test
    void nominateSong_succeeds_whenOpen() {
        var playlist = playlist(1L, PlaylistStatus.OPEN);
        when(playlistRepository.findById(TENANT_ID, 1L)).thenReturn(Optional.of(playlist));
        when(songRepository.findById(TENANT_ID, 3L)).thenReturn(Optional.of(song(3L)));
        when(contributorRepository.findById(TENANT_ID, 2L)).thenReturn(Optional.of(contributor(2L)));
        when(nominationRepository.existsByPlaylistIdAndSongId(1L, 3L)).thenReturn(false);
        when(nominationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = service.nominateSong(id(1L), NominateSongRequest.builder()
                .contributorId(IdGenerator.format("cont", 2L))
                .songId(IdGenerator.format("song", 3L))
                .build());

        assertThat(response.status()).isEqualTo(NominationStatus.PENDING);
        assertThat(response.playlistId()).isEqualTo(id(1L));
    }

    @Test
    void nominateSong_throws_whenNotOpen() {
        when(playlistRepository.findById(TENANT_ID, 1L)).thenReturn(Optional.of(playlist(1L, PlaylistStatus.NEW)));

        assertThatThrownBy(() -> service.nominateSong(id(1L), NominateSongRequest.builder()
                .contributorId(IdGenerator.format("cont", 2L)).songId(IdGenerator.format("song", 3L)).build()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void nominateSong_throws_whenDeadlinePassed() {
        var playlist = playlist(1L, PlaylistStatus.OPEN);
        playlist.setDeadline(Instant.now().minusSeconds(60));
        when(playlistRepository.findById(TENANT_ID, 1L)).thenReturn(Optional.of(playlist));

        assertThatThrownBy(() -> service.nominateSong(id(1L), NominateSongRequest.builder()
                .contributorId(IdGenerator.format("cont", 2L)).songId(IdGenerator.format("song", 3L)).build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("deadline");
    }

    @Test
    void nominateSong_throws_whenAlreadyNominated() {
        var playlist = playlist(1L, PlaylistStatus.OPEN);
        when(playlistRepository.findById(TENANT_ID, 1L)).thenReturn(Optional.of(playlist));
        when(songRepository.findById(TENANT_ID, 3L)).thenReturn(Optional.of(song(3L)));
        when(contributorRepository.findById(TENANT_ID, 2L)).thenReturn(Optional.of(contributor(2L)));
        when(nominationRepository.existsByPlaylistIdAndSongId(1L, 3L)).thenReturn(true);

        assertThatThrownBy(() -> service.nominateSong(id(1L), NominateSongRequest.builder()
                .contributorId(IdGenerator.format("cont", 2L)).songId(IdGenerator.format("song", 3L)).build()))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---- review (approve/decline) ----

    @Test
    void approveNomination_succeeds_whenPendingAndReviewerIsLead() {
        var nomination = nomination(10L, 1L, 3L, 2L, NominationStatus.PENDING.name());
        var playlist = playlist(1L, PlaylistStatus.OPEN);
        playlist.setLeadContributorId(2L);
        when(nominationRepository.findById(TENANT_ID, 10L)).thenReturn(Optional.of(nomination));
        when(playlistRepository.findById(TENANT_ID, 1L)).thenReturn(Optional.of(playlist));
        when(nominationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = service.approveNomination(IdGenerator.format("nom", 10L),
                ReviewNominationRequest.builder().reviewerId(IdGenerator.format("cont", 2L)).build());

        assertThat(response.status()).isEqualTo(NominationStatus.APPROVED);
    }

    @Test
    void declineNomination_throws_whenNotPending() {
        var nomination = nomination(10L, 1L, 3L, 2L, NominationStatus.APPROVED.name());
        when(nominationRepository.findById(TENANT_ID, 10L)).thenReturn(Optional.of(nomination));

        assertThatThrownBy(() -> service.declineNomination(IdGenerator.format("nom", 10L),
                ReviewNominationRequest.builder().reviewerId(IdGenerator.format("cont", 2L)).build()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reviewNomination_throws_whenReviewerIsNotLead() {
        var nomination = nomination(10L, 1L, 3L, 2L, NominationStatus.PENDING.name());
        var playlist = playlist(1L, PlaylistStatus.OPEN);
        playlist.setLeadContributorId(99L);
        when(nominationRepository.findById(TENANT_ID, 10L)).thenReturn(Optional.of(nomination));
        when(playlistRepository.findById(TENANT_ID, 1L)).thenReturn(Optional.of(playlist));

        assertThatThrownBy(() -> service.approveNomination(IdGenerator.format("nom", 10L),
                ReviewNominationRequest.builder().reviewerId(IdGenerator.format("cont", 2L)).build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lead contributor");
    }

    // ---- startGuessing ----

    @Test
    void startGuessing_succeeds_whenNoPendingNominations() {
        var playlist = playlist(1L, PlaylistStatus.OPEN);
        playlist.setLeadContributorId(2L);
        when(playlistRepository.findById(TENANT_ID, 1L)).thenReturn(Optional.of(playlist));
        when(nominationRepository.findByPlaylistId(1L)).thenReturn(List.of(
                nomination(10L, 1L, 3L, 2L, NominationStatus.APPROVED.name())));
        when(nominationRepository.declinePendingByPlaylistId(eq(1L), any())).thenReturn(0);
        when(playlistRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(playlistMapper.toResponse(any())).thenReturn(PlaylistResponse.builder().id(id(1L)).build());

        service.startGuessing(id(1L), StartGuessingRequest.builder()
                .contributorId(IdGenerator.format("cont", 2L)).build());

        var captor = ArgumentCaptor.forClass(PlaylistItem.class);
        verify(playlistRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PlaylistStatus.GUESSING.name());
        assertThat(captor.getValue().getGuessingDeadline()).isNotNull();
    }

    @Test
    void startGuessing_throws_whenDeadlineNotPassedAndPendingExists() {
        var playlist = playlist(1L, PlaylistStatus.OPEN);
        playlist.setLeadContributorId(2L);
        playlist.setDeadline(Instant.now().plusSeconds(3600));
        when(playlistRepository.findById(TENANT_ID, 1L)).thenReturn(Optional.of(playlist));
        when(nominationRepository.findByPlaylistId(1L)).thenReturn(List.of(
                nomination(10L, 1L, 3L, 2L, NominationStatus.PENDING.name())));

        assertThatThrownBy(() -> service.startGuessing(id(1L), StartGuessingRequest.builder()
                .contributorId(IdGenerator.format("cont", 2L)).build()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void startGuessing_throws_whenNotLeadContributor() {
        var playlist = playlist(1L, PlaylistStatus.OPEN);
        playlist.setLeadContributorId(99L);
        when(playlistRepository.findById(TENANT_ID, 1L)).thenReturn(Optional.of(playlist));

        assertThatThrownBy(() -> service.startGuessing(id(1L), StartGuessingRequest.builder()
                .contributorId(IdGenerator.format("cont", 2L)).build()))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---- submitGuesses ----

    @Test
    void submitGuesses_deletesThenRecreates() {
        var playlist = playlist(1L, PlaylistStatus.GUESSING);
        when(playlistRepository.findById(TENANT_ID, 1L)).thenReturn(Optional.of(playlist));
        when(contributorRepository.findById(TENANT_ID, 2L)).thenReturn(Optional.of(contributor(2L)));
        when(nominationRepository.findById(eq(TENANT_ID), anyLong()))
                .thenReturn(Optional.of(nomination(10L, 1L, 3L, 5L, NominationStatus.APPROVED.name())));
        when(guessSubmissionRepository.existsByPlaylistIdAndContributorId(1L, 2L)).thenReturn(false);

        service.submitGuesses(id(1L), SubmitGuessesRequest.builder()
                .contributorId(IdGenerator.format("cont", 2L))
                .guesses(List.of(SubmitGuessesRequest.GuessSelection.builder()
                        .nominationId(IdGenerator.format("nom", 10L))
                        .guessedContributorId(IdGenerator.format("cont", 5L))
                        .build()))
                .build());

        verify(guessRepository).deleteByPlaylistAndGuesser(1L, 2L);
        verify(guessRepository).save(any(GuessItem.class));
        verify(guessSubmissionRepository).save(any());
    }

    @Test
    void submitGuesses_throws_whenNotGuessing() {
        when(playlistRepository.findById(TENANT_ID, 1L)).thenReturn(Optional.of(playlist(1L, PlaylistStatus.OPEN)));

        assertThatThrownBy(() -> service.submitGuesses(id(1L), SubmitGuessesRequest.builder()
                .contributorId(IdGenerator.format("cont", 2L)).guesses(List.of()).build()))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---- publishPlaylist / ranking computation ----

    @Test
    void publishPlaylist_computesStandardCompetitionRanking() {
        var playlist = playlist(1L, PlaylistStatus.GUESSING);
        playlist.setLeadContributorId(2L);
        when(playlistRepository.findById(TENANT_ID, 1L)).thenReturn(Optional.of(playlist));
        when(playlistRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(playlistMapper.toResponse(any())).thenReturn(PlaylistResponse.builder().id(id(1L)).build());

        // nomination 10 nominated by 100 (approved), nomination 11 nominated by 101 (approved)
        when(nominationRepository.findByPlaylistId(1L)).thenReturn(List.of(
                nomination(10L, 1L, 3L, 100L, NominationStatus.APPROVED.name()),
                nomination(11L, 1L, 4L, 101L, NominationStatus.APPROVED.name())));

        // guesser 200 gets both right (2 correct), guesser 201 gets 1 right (tied would need another 1-correct guesser)
        GuessItem g1 = guess(10L, 200L, 100L); // correct
        GuessItem g2 = guess(11L, 200L, 101L); // correct
        GuessItem g3 = guess(10L, 201L, 999L); // wrong
        GuessItem g4 = guess(11L, 201L, 101L); // correct
        when(guessRepository.findByPlaylistId(1L)).thenReturn(List.of(g1, g2, g3, g4));
        when(playlistRankingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.publishPlaylist(id(1L), PublishPlaylistRequest.builder()
                .contributorId(IdGenerator.format("cont", 2L)).build());

        var captor = ArgumentCaptor.forClass(PlaylistRankingItem.class);
        verify(playlistRankingRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        var rankings = captor.getAllValues();

        var byContributor = rankings.stream()
                .collect(java.util.stream.Collectors.toMap(PlaylistRankingItem::getContributorId, r -> r));
        assertThat(byContributor.get(200L).getCorrectGuesses()).isEqualTo(2);
        assertThat(byContributor.get(200L).getRankPosition()).isEqualTo(1);
        assertThat(byContributor.get(201L).getCorrectGuesses()).isEqualTo(1);
        assertThat(byContributor.get(201L).getTotalGuesses()).isEqualTo(2);
        assertThat(byContributor.get(201L).getRankPosition()).isEqualTo(2);
    }

    private static GuessItem guess(long nominationId, long guesserId, long guessedContributorId) {
        GuessItem item = new GuessItem();
        item.setId(IdGenerator.generate());
        item.setTenantId(TENANT_ID);
        item.setPlaylistId(1L);
        item.setNominationId(nominationId);
        item.setGuesserId(guesserId);
        item.setGuessedContributorId(guessedContributorId);
        return item;
    }

    @Test
    void publishPlaylist_throws_whenNotGuessing() {
        when(playlistRepository.findById(TENANT_ID, 1L)).thenReturn(Optional.of(playlist(1L, PlaylistStatus.OPEN)));

        assertThatThrownBy(() -> service.publishPlaylist(id(1L),
                PublishPlaylistRequest.builder().contributorId(IdGenerator.format("cont", 2L)).build()))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---- submitRatings ----

    @Test
    void submitRatings_succeeds_withValidLinearPointSet() {
        var playlist = playlist(1L, PlaylistStatus.PUBLISHED);
        playlist.setRatingType("LINEAR");
        when(playlistRepository.findById(TENANT_ID, 1L)).thenReturn(Optional.of(playlist));
        when(contributorRepository.findById(TENANT_ID, 2L)).thenReturn(Optional.of(contributor(2L)));
        when(nominationRepository.findById(eq(TENANT_ID), anyLong())).thenAnswer(inv ->
                Optional.of(nomination(inv.getArgument(1), 1L, 3L, 999L, NominationStatus.APPROVED.name())));

        service.submitRatings(id(1L), SubmitRatingsRequest.builder()
                .contributorId(IdGenerator.format("cont", 2L))
                .ratings(List.of(
                        rating(10L, 1),
                        rating(11L, 2),
                        rating(12L, 3)))
                .build());

        verify(songRatingRepository).deleteByPlaylistAndContributor(1L, 2L);
        verify(songRatingRepository, org.mockito.Mockito.times(3)).save(any());
    }

    private static SubmitRatingsRequest.RatingEntry rating(long nominationId, int points) {
        return SubmitRatingsRequest.RatingEntry.builder()
                .nominationId(IdGenerator.format("nom", nominationId))
                .points(points)
                .build();
    }

    @Test
    void submitRatings_throws_whenPointSetDoesNotMatchRatingType() {
        var playlist = playlist(1L, PlaylistStatus.PUBLISHED);
        playlist.setRatingType("LINEAR");
        when(playlistRepository.findById(TENANT_ID, 1L)).thenReturn(Optional.of(playlist));
        when(contributorRepository.findById(TENANT_ID, 2L)).thenReturn(Optional.of(contributor(2L)));

        assertThatThrownBy(() -> service.submitRatings(id(1L), SubmitRatingsRequest.builder()
                .contributorId(IdGenerator.format("cont", 2L))
                .ratings(List.of(rating(10L, 1), rating(11L, 1)))
                .build()))
                .isInstanceOf(IllegalStateException.class);

        verify(songRatingRepository, never()).deleteByPlaylistAndContributor(anyLong(), anyLong());
    }

    @Test
    void submitRatings_throws_whenRatingOwnNomination() {
        var playlist = playlist(1L, PlaylistStatus.PUBLISHED);
        playlist.setRatingType("BEST_SONG");
        when(playlistRepository.findById(TENANT_ID, 1L)).thenReturn(Optional.of(playlist));
        when(contributorRepository.findById(TENANT_ID, 2L)).thenReturn(Optional.of(contributor(2L)));
        when(nominationRepository.findById(TENANT_ID, 10L))
                .thenReturn(Optional.of(nomination(10L, 1L, 3L, 2L, NominationStatus.APPROVED.name())));

        assertThatThrownBy(() -> service.submitRatings(id(1L), SubmitRatingsRequest.builder()
                .contributorId(IdGenerator.format("cont", 2L))
                .ratings(List.of(rating(10L, 1)))
                .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("own nomination");
    }

    @Test
    void submitRatings_throws_whenNotPublished() {
        when(playlistRepository.findById(TENANT_ID, 1L)).thenReturn(Optional.of(playlist(1L, PlaylistStatus.GUESSING)));

        assertThatThrownBy(() -> service.submitRatings(id(1L), SubmitRatingsRequest.builder()
                .contributorId(IdGenerator.format("cont", 2L)).ratings(List.of(rating(10L, 1))).build()))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---- reads ----

    @Test
    void getGuesses_mapsToResponse() {
        when(guessRepository.findByPlaylistId(1L)).thenReturn(List.of(guess(10L, 200L, 100L)));

        var result = service.getGuesses(id(1L));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).nominationId()).isEqualTo(IdGenerator.format("nom", 10L));
    }

    @Test
    void getRankings_sortsByPlaylistThenRank() {
        var r1 = rankingItem(1L, 1, 200L);
        var r2 = rankingItem(1L, 2, 201L);
        when(playlistRankingRepository.findAllByTenant(TENANT_ID)).thenReturn(List.of(r2, r1));
        when(playlistRepository.findById(eq(TENANT_ID), anyLong())).thenReturn(Optional.of(playlist(1L, PlaylistStatus.PUBLISHED)));
        when(contributorRepository.findById(eq(TENANT_ID), anyLong())).thenReturn(Optional.of(contributor(200L)));

        var result = service.getRankings();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).rankPosition()).isEqualTo(1);
        assertThat(result.get(1).rankPosition()).isEqualTo(2);
    }

    private static PlaylistRankingItem rankingItem(long playlistId, int rank, long contributorId) {
        PlaylistRankingItem item = new PlaylistRankingItem();
        item.setId(IdGenerator.generate());
        item.setTenantId(TENANT_ID);
        item.setPlaylistId(playlistId);
        item.setContributorId(contributorId);
        item.setRankPosition(rank);
        item.setCorrectGuesses(rank == 1 ? 2 : 1);
        item.setTotalGuesses(2);
        return item;
    }

    @Test
    void getSongRatings_mapsToResponse() {
        var r = new com.orgasm.dynamo.rating.SongRatingItem();
        r.setId(1L);
        r.setNominationId(10L);
        r.setContributorId(2L);
        r.setPoints(3);
        when(songRatingRepository.findByPlaylistId(1L)).thenReturn(List.of(r));
        when(contributorRepository.findById(TENANT_ID, 2L)).thenReturn(Optional.of(contributor(2L)));

        var result = service.getSongRatings(id(1L));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).points()).isEqualTo(3);
        assertThat(result.get(0).contributorName()).isEqualTo("Ada");
    }
}
