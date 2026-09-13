package com.orgasm.dynamo.orgasm;

import com.orgasm.dynamo.contributor.ContributorDynamoRepository;
import com.orgasm.dynamo.contributor.ContributorItem;
import com.orgasm.dynamo.domain.IdGenerator;
import com.orgasm.dynamo.guessing.GuessDynamoRepository;
import com.orgasm.dynamo.guessing.GuessItem;
import com.orgasm.dynamo.guessing.GuessSubmissionDynamoRepository;
import com.orgasm.dynamo.guessing.GuessSubmissionItem;
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
import com.orgasm.dynamo.rating.SongRatingItem;
import com.orgasm.dynamo.song.SongDynamoRepository;
import com.orgasm.dynamo.tenant.DynamoTenantContext;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrgasmService {

    private static final String PLAYLIST_PREFIX = "play";
    private static final String SONG_PREFIX = "song";
    private static final String CONTRIBUTOR_PREFIX = "cont";
    private static final String NOMINATION_PREFIX = "nom";

    private final PlaylistDynamoRepository playlistRepository;
    private final PlaylistMapper playlistMapper;
    private final ContributorDynamoRepository contributorRepository;
    private final SongDynamoRepository songRepository;
    private final NominationDynamoRepository nominationRepository;
    private final GuessDynamoRepository guessRepository;
    private final GuessSubmissionDynamoRepository guessSubmissionRepository;
    private final SongRatingDynamoRepository songRatingRepository;
    private final PlaylistRankingDynamoRepository playlistRankingRepository;

    @CircuitBreaker(name = "db")
    public PlaylistResponse openPlaylist(String playlistId, OpenPlaylistRequest request) {
        long tenantId = DynamoTenantContext.get();
        PlaylistItem playlist = requirePlaylist(tenantId, playlistId);
        if (!PlaylistStatus.NEW.name().equals(playlist.getStatus())) {
            throw new IllegalStateException("Playlist must be NEW to open");
        }
        long contributorId = IdGenerator.parse(request.contributorId());
        requireContributor(tenantId, contributorId);

        playlist.setDeadline(request.deadline());
        playlist.setLeadContributorId(contributorId);
        playlist.setStatus(PlaylistStatus.OPEN.name());
        playlist.setUpdatedAt(Instant.now());

        return toPlaylistResponse(tenantId, playlistRepository.save(playlist));
    }

    @CircuitBreaker(name = "db")
    public Page<PlaylistResponse> findPlaylistsByContributor(String contributorId, Pageable pageable) {
        long tenantId = DynamoTenantContext.get();
        long parsedContributorId = IdGenerator.parse(contributorId);
        List<PlaylistItem> items = playlistRepository.findAllByTenant(tenantId).stream()
                .filter(item -> item.getDeletedAt() == null)
                .filter(item -> parsedContributorId == safeLong(item.getLeadContributorId()))
                .toList();

        if (pageable.isUnpaged()) {
            return new PageImpl<>(items.stream().map(item -> toPlaylistResponse(tenantId, item)).toList());
        }
        int from = Math.min((int) pageable.getOffset(), items.size());
        int to = Math.min(from + pageable.getPageSize(), items.size());
        List<PlaylistResponse> page =
                items.subList(from, to).stream().map(item -> toPlaylistResponse(tenantId, item)).toList();
        return new PageImpl<>(page, pageable, items.size());
    }

    @CircuitBreaker(name = "db")
    public NominationResponse nominateSong(String playlistId, NominateSongRequest request) {
        long tenantId = DynamoTenantContext.get();
        PlaylistItem playlist = requirePlaylist(tenantId, playlistId);
        if (!PlaylistStatus.OPEN.name().equals(playlist.getStatus())) {
            throw new IllegalStateException("Playlist must be OPEN to nominate a song");
        }
        if (playlist.getDeadline() != null && Instant.now().isAfter(playlist.getDeadline())) {
            throw new IllegalStateException("Nomination deadline has passed");
        }
        long songId = IdGenerator.parse(request.songId());
        requireSong(tenantId, songId);
        long contributorId = IdGenerator.parse(request.contributorId());
        requireContributor(tenantId, contributorId);

        long playlistDbId = IdGenerator.parse(playlistId);
        if (nominationRepository.existsByPlaylistIdAndSongId(playlistDbId, songId)) {
            throw new IllegalStateException("Song has already been nominated for this playlist");
        }

        Instant now = Instant.now();
        long id = IdGenerator.generate();
        NominationItem item = new NominationItem();
        item.setPk(NominationItem.partitionKey(tenantId));
        item.setSk(String.valueOf(id));
        item.setId(id);
        item.setTenantId(tenantId);
        item.setPlaylistId(playlistDbId);
        item.setSongId(songId);
        item.setNominatedById(contributorId);
        item.setStatus(NominationStatus.PENDING.name());
        item.setCreatedAt(now);
        item.setUpdatedAt(now);

        return toNominationResponse(nominationRepository.save(item));
    }

    @CircuitBreaker(name = "db")
    public Page<NominationResponse> findNominations(String playlistId, Pageable pageable) {
        long playlistDbId = IdGenerator.parse(playlistId);
        List<NominationItem> items = nominationRepository.findByPlaylistId(playlistDbId).stream()
                .filter(item -> item.getDeletedAt() == null)
                .toList();

        if (pageable.isUnpaged()) {
            return new PageImpl<>(items.stream().map(this::toNominationResponse).toList());
        }
        int from = Math.min((int) pageable.getOffset(), items.size());
        int to = Math.min(from + pageable.getPageSize(), items.size());
        List<NominationResponse> page = items.subList(from, to).stream().map(this::toNominationResponse).toList();
        return new PageImpl<>(page, pageable, items.size());
    }

    @CircuitBreaker(name = "db")
    public List<SongNominationResponse> findNominationsBySong(String songId) {
        long tenantId = DynamoTenantContext.get();
        long songDbId = IdGenerator.parse(songId);
        return nominationRepository.findBySongId(songDbId).stream()
                .filter(item -> item.getDeletedAt() == null)
                .map(item -> {
                    PlaylistItem playlist =
                            playlistRepository.findById(tenantId, item.getPlaylistId()).orElse(null);
                    ContributorItem nominatedBy =
                            contributorRepository.findById(tenantId, item.getNominatedById()).orElse(null);
                    return SongNominationResponse.builder()
                            .id(IdGenerator.format(NOMINATION_PREFIX, item.getId()))
                            .playlistId(IdGenerator.format(PLAYLIST_PREFIX, item.getPlaylistId()))
                            .playlistName(playlist != null ? playlist.getName() : null)
                            .nominatedById(IdGenerator.format(CONTRIBUTOR_PREFIX, item.getNominatedById()))
                            .nominatedByName(nominatedBy != null ? nominatedBy.getName() : null)
                            .status(NominationStatus.valueOf(item.getStatus()))
                            .build();
                })
                .toList();
    }

    @CircuitBreaker(name = "db")
    public NominationResponse approveNomination(String nominationId, ReviewNominationRequest request) {
        return reviewNomination(nominationId, request.reviewerId(), NominationStatus.APPROVED);
    }

    @CircuitBreaker(name = "db")
    public NominationResponse declineNomination(String nominationId, ReviewNominationRequest request) {
        return reviewNomination(nominationId, request.reviewerId(), NominationStatus.DECLINED);
    }

    private NominationResponse reviewNomination(String nominationId, String reviewerId, NominationStatus newStatus) {
        long tenantId = DynamoTenantContext.get();
        NominationItem nomination = requireNomination(tenantId, nominationId);
        if (!NominationStatus.PENDING.name().equals(nomination.getStatus())) {
            throw new IllegalStateException("Nomination is not pending");
        }
        PlaylistItem playlist = requirePlaylistByDbId(tenantId, nomination.getPlaylistId());
        long reviewerDbId = IdGenerator.parse(reviewerId);
        if (!isLeadContributor(playlist, reviewerDbId)) {
            throw new IllegalStateException("Only the lead contributor can review nominations");
        }

        nomination.setStatus(newStatus.name());
        nomination.setUpdatedAt(Instant.now());
        return toNominationResponse(nominationRepository.save(nomination));
    }

    private boolean isLeadContributor(PlaylistItem playlist, long contributorDbId) {
        return playlist.getLeadContributorId() != null && playlist.getLeadContributorId() == contributorDbId;
    }

    @CircuitBreaker(name = "db")
    public PlaylistResponse startGuessing(String playlistId, StartGuessingRequest request) {
        long tenantId = DynamoTenantContext.get();
        PlaylistItem playlist = requirePlaylist(tenantId, playlistId);
        if (!PlaylistStatus.OPEN.name().equals(playlist.getStatus())) {
            throw new IllegalStateException("Playlist must be OPEN to start guessing");
        }
        long contributorId = IdGenerator.parse(request.contributorId());
        if (!isLeadContributor(playlist, contributorId)) {
            throw new IllegalStateException("Only the lead contributor can start guessing");
        }

        long playlistDbId = IdGenerator.parse(playlistId);
        boolean deadlinePassed = playlist.getDeadline() == null || Instant.now().isAfter(playlist.getDeadline());
        boolean noPending = nominationRepository.findByPlaylistId(playlistDbId).stream()
                .filter(item -> item.getDeletedAt() == null)
                .noneMatch(item -> NominationStatus.PENDING.name().equals(item.getStatus()));
        if (!deadlinePassed && !noPending) {
            throw new IllegalStateException("Deadline has not passed and there are still pending nominations");
        }

        Instant now = Instant.now();
        nominationRepository.declinePendingByPlaylistId(playlistDbId, now);

        playlist.setGuessingDeadline(now.plus(7, ChronoUnit.DAYS));
        playlist.setStatus(PlaylistStatus.GUESSING.name());
        playlist.setUpdatedAt(now);

        return toPlaylistResponse(tenantId, playlistRepository.save(playlist));
    }

    @CircuitBreaker(name = "db")
    public void submitGuesses(String playlistId, SubmitGuessesRequest request) {
        long tenantId = DynamoTenantContext.get();
        PlaylistItem playlist = requirePlaylist(tenantId, playlistId);
        if (!PlaylistStatus.GUESSING.name().equals(playlist.getStatus())) {
            throw new IllegalStateException("Playlist must be GUESSING to submit guesses");
        }
        long contributorId = IdGenerator.parse(request.contributorId());
        requireContributor(tenantId, contributorId);

        long playlistDbId = IdGenerator.parse(playlistId);
        guessRepository.deleteByPlaylistAndGuesser(playlistDbId, contributorId);

        Instant now = Instant.now();
        if (request.guesses() != null) {
            for (SubmitGuessesRequest.GuessSelection selection : request.guesses()) {
                long nominationDbId = IdGenerator.parse(selection.nominationId());
                nominationRepository.findById(tenantId, nominationDbId)
                        .orElseThrow(() -> new NoSuchElementException("Nomination not found: " + selection.nominationId()));
                long guessedContributorId = IdGenerator.parse(selection.guessedContributorId());

                long id = IdGenerator.generate();
                GuessItem guess = new GuessItem();
                guess.setPk(GuessItem.partitionKey(tenantId));
                guess.setSk(String.valueOf(id));
                guess.setId(id);
                guess.setTenantId(tenantId);
                guess.setPlaylistId(playlistDbId);
                guess.setNominationId(nominationDbId);
                guess.setGuesserId(contributorId);
                guess.setGuessedContributorId(guessedContributorId);
                guess.setCreatedAt(now);
                guessRepository.save(guess);
            }
        }

        if (!guessSubmissionRepository.existsByPlaylistIdAndContributorId(playlistDbId, contributorId)) {
            long id = IdGenerator.generate();
            GuessSubmissionItem submission = new GuessSubmissionItem();
            submission.setPk(GuessSubmissionItem.partitionKey(tenantId));
            submission.setSk(String.valueOf(id));
            submission.setId(id);
            submission.setTenantId(tenantId);
            submission.setPlaylistId(playlistDbId);
            submission.setContributorId(contributorId);
            submission.setCreatedAt(now);
            submission.setUpdatedAt(now);
            guessSubmissionRepository.save(submission);
        }
    }

    @CircuitBreaker(name = "db")
    public List<GuessResponse> getGuesses(String playlistId) {
        long playlistDbId = IdGenerator.parse(playlistId);
        return guessRepository.findByPlaylistId(playlistDbId).stream()
                .map(item -> GuessResponse.builder()
                        .nominationId(IdGenerator.format(NOMINATION_PREFIX, item.getNominationId()))
                        .guesserId(IdGenerator.format(CONTRIBUTOR_PREFIX, item.getGuesserId()))
                        .guessedContributorId(IdGenerator.format(CONTRIBUTOR_PREFIX, item.getGuessedContributorId()))
                        .build())
                .toList();
    }

    @CircuitBreaker(name = "db")
    public PlaylistResponse publishPlaylist(String playlistId, PublishPlaylistRequest request) {
        long tenantId = DynamoTenantContext.get();
        PlaylistItem playlist = requirePlaylist(tenantId, playlistId);
        if (!PlaylistStatus.GUESSING.name().equals(playlist.getStatus())) {
            throw new IllegalStateException("Playlist must be GUESSING to publish");
        }
        long contributorId = IdGenerator.parse(request.contributorId());
        if (!isLeadContributor(playlist, contributorId)) {
            throw new IllegalStateException("Only the lead contributor can publish");
        }

        playlist.setStatus(PlaylistStatus.PUBLISHED.name());
        playlist.setUpdatedAt(Instant.now());
        PlaylistItem saved = playlistRepository.save(playlist);

        saveRankings(tenantId, IdGenerator.parse(playlistId));

        return toPlaylistResponse(tenantId, saved);
    }

    private void saveRankings(long tenantId, long playlistDbId) {
        Map<Long, Long> approvedNominators = nominationRepository.findByPlaylistId(playlistDbId).stream()
                .filter(item -> NominationStatus.APPROVED.name().equals(item.getStatus()))
                .collect(Collectors.toMap(NominationItem::getId, NominationItem::getNominatedById));

        List<GuessItem> guesses = guessRepository.findByPlaylistId(playlistDbId);
        Map<Long, List<GuessItem>> byGuesser = guesses.stream().collect(Collectors.groupingBy(GuessItem::getGuesserId));

        record Tally(long guesserId, int correct, int total) {}
        List<Tally> tallies = byGuesser.entrySet().stream()
                .map(entry -> {
                    long guesserId = entry.getKey();
                    List<GuessItem> items = entry.getValue();
                    int total = items.size();
                    int correct = (int) items.stream()
                            .filter(item -> {
                                Long nominator = approvedNominators.get(item.getNominationId());
                                return nominator != null && nominator.equals(item.getGuessedContributorId());
                            })
                            .count();
                    return new Tally(guesserId, correct, total);
                })
                .sorted(Comparator.comparingInt(Tally::correct).reversed())
                .toList();

        Instant now = Instant.now();
        int rank = 0;
        int previousCorrect = -1;
        int position = 0;
        for (Tally tally : tallies) {
            position++;
            if (tally.correct() != previousCorrect) {
                rank = position;
                previousCorrect = tally.correct();
            }

            long id = IdGenerator.generate();
            PlaylistRankingItem item = new PlaylistRankingItem();
            item.setPk(PlaylistRankingItem.partitionKey(tenantId));
            item.setSk(String.valueOf(id));
            item.setId(id);
            item.setTenantId(tenantId);
            item.setPlaylistId(playlistDbId);
            item.setContributorId(tally.guesserId());
            item.setRankPosition(rank);
            item.setCorrectGuesses(tally.correct());
            item.setTotalGuesses(tally.total());
            item.setCreatedAt(now);
            item.setUpdatedAt(now);
            playlistRankingRepository.save(item);
        }
    }

    @CircuitBreaker(name = "db")
    public List<RankingResponse> getRankings() {
        long tenantId = DynamoTenantContext.get();
        List<PlaylistRankingItem> rankings = playlistRankingRepository.findAllByTenant(tenantId).stream()
                .filter(item -> item.getDeletedAt() == null)
                .sorted(Comparator.comparing(PlaylistRankingItem::getPlaylistId)
                        .thenComparing(PlaylistRankingItem::getRankPosition))
                .toList();

        return rankings.stream()
                .map(item -> {
                    PlaylistItem playlist = playlistRepository.findById(tenantId, item.getPlaylistId()).orElse(null);
                    ContributorItem contributor =
                            contributorRepository.findById(tenantId, item.getContributorId()).orElse(null);
                    return RankingResponse.builder()
                            .playlistId(IdGenerator.format(PLAYLIST_PREFIX, item.getPlaylistId()))
                            .playlistName(playlist != null ? playlist.getName() : null)
                            .contributorId(IdGenerator.format(CONTRIBUTOR_PREFIX, item.getContributorId()))
                            .contributorName(contributor != null ? contributor.getName() : null)
                            .contributorAvatarUrl(contributor != null ? contributor.getAvatarUrl() : null)
                            .rankPosition(item.getRankPosition())
                            .correctGuesses(item.getCorrectGuesses())
                            .totalGuesses(item.getTotalGuesses())
                            .build();
                })
                .toList();
    }

    @CircuitBreaker(name = "db")
    public void submitRatings(String playlistId, SubmitRatingsRequest request) {
        long tenantId = DynamoTenantContext.get();
        PlaylistItem playlist = requirePlaylist(tenantId, playlistId);
        if (!PlaylistStatus.PUBLISHED.name().equals(playlist.getStatus())) {
            throw new IllegalStateException("Playlist must be PUBLISHED to submit ratings");
        }
        if (playlist.getRatingType() == null) {
            throw new IllegalStateException("Playlist has no rating type configured");
        }
        long contributorId = IdGenerator.parse(request.contributorId());
        requireContributor(tenantId, contributorId);
        validateRatings(playlist.getRatingType(), request.ratings());

        long playlistDbId = IdGenerator.parse(playlistId);
        songRatingRepository.deleteByPlaylistAndContributor(playlistDbId, contributorId);

        Instant now = Instant.now();
        for (SubmitRatingsRequest.RatingEntry entry : request.ratings()) {
            long nominationDbId = IdGenerator.parse(entry.nominationId());
            NominationItem nomination = nominationRepository.findById(tenantId, nominationDbId)
                    .orElseThrow(() -> new NoSuchElementException("Nomination not found: " + entry.nominationId()));
            if (nomination.getNominatedById() != null && nomination.getNominatedById() == contributorId) {
                throw new IllegalStateException("Cannot rate your own nomination");
            }

            long id = IdGenerator.generate();
            SongRatingItem item = new SongRatingItem();
            item.setPk(SongRatingItem.partitionKey(tenantId));
            item.setSk(String.valueOf(id));
            item.setId(id);
            item.setTenantId(tenantId);
            item.setPlaylistId(playlistDbId);
            item.setContributorId(contributorId);
            item.setNominationId(nominationDbId);
            item.setPoints(entry.points());
            item.setCreatedAt(now);
            item.setUpdatedAt(now);
            songRatingRepository.save(item);
        }
    }

    private void validateRatings(String ratingType, List<SubmitRatingsRequest.RatingEntry> ratings) {
        List<Integer> expected = switch (ratingType) {
            case "LINEAR" -> List.of(1, 2, 3);
            case "FIBONACCI" -> List.of(5, 8, 13);
            case "BEST_SONG" -> List.of(1);
            default -> throw new IllegalStateException("Unknown rating type: " + ratingType);
        };
        List<Integer> submitted =
                ratings.stream().map(SubmitRatingsRequest.RatingEntry::points).sorted().toList();
        if (!submitted.equals(expected)) {
            throw new IllegalStateException("Ratings must be exactly " + expected);
        }
    }

    @CircuitBreaker(name = "db")
    public List<SongRatingResponse> getSongRatings(String playlistId) {
        long tenantId = DynamoTenantContext.get();
        long playlistDbId = IdGenerator.parse(playlistId);
        return songRatingRepository.findByPlaylistId(playlistDbId).stream()
                .filter(item -> item.getDeletedAt() == null)
                .map(item -> {
                    ContributorItem contributor =
                            contributorRepository.findById(tenantId, item.getContributorId()).orElse(null);
                    return SongRatingResponse.builder()
                            .nominationId(IdGenerator.format(NOMINATION_PREFIX, item.getNominationId()))
                            .contributorId(IdGenerator.format(CONTRIBUTOR_PREFIX, item.getContributorId()))
                            .contributorName(contributor != null ? contributor.getName() : null)
                            .points(item.getPoints())
                            .build();
                })
                .toList();
    }

    private PlaylistResponse toPlaylistResponse(long tenantId, PlaylistItem playlist) {
        PlaylistResponse base = playlistMapper.toResponse(playlist);
        if (playlist.getLeadContributorId() == null) {
            return base;
        }
        ContributorItem lead = contributorRepository.findById(tenantId, playlist.getLeadContributorId()).orElse(null);
        if (lead == null) {
            return base;
        }
        return PlaylistResponse.builder()
                .id(base.id())
                .name(base.name())
                .description(base.description())
                .status(base.status())
                .ratingType(base.ratingType())
                .leadContributorId(base.leadContributorId())
                .leadContributorName(lead.getName())
                .leadContributorAvatarUrl(lead.getAvatarUrl())
                .deadline(base.deadline())
                .guessingDeadline(base.guessingDeadline())
                .version(base.version())
                .createdAt(base.createdAt())
                .updatedAt(base.updatedAt())
                .build();
    }

    private NominationResponse toNominationResponse(NominationItem item) {
        return NominationResponse.builder()
                .id(IdGenerator.format(NOMINATION_PREFIX, item.getId()))
                .playlistId(IdGenerator.format(PLAYLIST_PREFIX, item.getPlaylistId()))
                .songId(IdGenerator.format(SONG_PREFIX, item.getSongId()))
                .nominatedById(IdGenerator.format(CONTRIBUTOR_PREFIX, item.getNominatedById()))
                .status(NominationStatus.valueOf(item.getStatus()))
                .version(item.getVersion())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    private PlaylistItem requirePlaylist(long tenantId, String playlistId) {
        return requirePlaylistByDbId(tenantId, IdGenerator.parse(playlistId));
    }

    private PlaylistItem requirePlaylistByDbId(long tenantId, long playlistDbId) {
        return playlistRepository.findById(tenantId, playlistDbId)
                .filter(item -> item.getDeletedAt() == null)
                .orElseThrow(() -> new NoSuchElementException("Playlist not found: " + playlistDbId));
    }

    private void requireContributor(long tenantId, long contributorId) {
        contributorRepository.findById(tenantId, contributorId)
                .filter(item -> item.getDeletedAt() == null)
                .orElseThrow(() -> new NoSuchElementException("Contributor not found: " + contributorId));
    }

    private void requireSong(long tenantId, long songId) {
        songRepository.findById(tenantId, songId)
                .filter(item -> item.getDeletedAt() == null)
                .orElseThrow(() -> new NoSuchElementException("Song not found: " + songId));
    }

    private NominationItem requireNomination(long tenantId, String nominationId) {
        return nominationRepository.findById(tenantId, IdGenerator.parse(nominationId))
                .filter(item -> item.getDeletedAt() == null)
                .orElseThrow(() -> new NoSuchElementException("Nomination not found: " + nominationId));
    }

    private static long safeLong(Long value) {
        return value != null ? value : -1L;
    }
}
