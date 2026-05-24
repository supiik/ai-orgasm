package com.orgasm.backend.orgasm;

import com.orgasm.backend.contributor.Contributor;
import com.orgasm.backend.contributor.ContributorRepository;
import com.orgasm.backend.domain.IdGenerator;
import com.orgasm.backend.guessing.Guess;
import com.orgasm.backend.guessing.GuessRepository;
import com.orgasm.backend.guessing.GuessSubmission;
import com.orgasm.backend.guessing.GuessSubmissionRepository;
import com.orgasm.backend.nomination.Nomination;
import com.orgasm.backend.nomination.NominationMapper;
import com.orgasm.backend.nomination.NominationRepository;
import com.orgasm.backend.nomination.NominationResponse;
import com.orgasm.backend.nomination.NominationStatus;
import com.orgasm.backend.nomination.SongNominationResponse;
import com.orgasm.backend.playlist.Playlist;
import com.orgasm.backend.playlist.PlaylistMapper;
import com.orgasm.backend.playlist.PlaylistRepository;
import com.orgasm.backend.playlist.PlaylistResponse;
import com.orgasm.backend.playlist.PlaylistStatus;
import com.orgasm.backend.playlist.RatingType;
import com.orgasm.backend.ranking.PlaylistRanking;
import com.orgasm.backend.ranking.PlaylistRankingRepository;
import com.orgasm.backend.ranking.RankingResponse;
import com.orgasm.backend.rating.SongRating;
import com.orgasm.backend.rating.SongRatingRepository;
import com.orgasm.backend.rating.SongRatingResponse;
import com.orgasm.backend.rating.SubmitRatingsRequest;
import com.orgasm.backend.song.Song;
import com.orgasm.backend.song.SongRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Transactional("appTransactionManager")
@RequiredArgsConstructor
public class OrgasmService {

    private final PlaylistRepository playlistRepository;
    private final ContributorRepository contributorRepository;
    private final SongRepository songRepository;
    private final NominationRepository nominationRepository;
    private final GuessSubmissionRepository guessSubmissionRepository;
    private final GuessRepository guessRepository;
    private final PlaylistRankingRepository playlistRankingRepository;
    private final SongRatingRepository songRatingRepository;
    private final PlaylistMapper playlistMapper;
    private final NominationMapper nominationMapper;

    @CircuitBreaker(name = "db")
    public PlaylistResponse openPlaylist(String playlistId, OpenPlaylistRequest request) {
        Playlist playlist = requirePlaylist(playlistId);
        if (playlist.getStatus() != PlaylistStatus.NEW) {
            throw new IllegalStateException("Only NEW playlists can be opened: " + playlist.getStatus());
        }
        long contributorDbId = IdGenerator.parse(request.contributorId());
        if (!contributorRepository.existsById(contributorDbId)) {
            throw new EntityNotFoundException("Contributor not found: " + request.contributorId());
        }
        playlist.setDeadline(request.deadline());
        playlist.setStatus(PlaylistStatus.OPEN);
        playlistRepository.save(playlist);
        playlistRepository.assignLeadContributor(playlist.getId(), contributorRepository.getReferenceById(contributorDbId));
        return playlistMapper.toResponse(playlistRepository.findById(playlist.getId()).orElseThrow());
    }

    @CircuitBreaker(name = "db")
    @Transactional(readOnly = true)
    public Page<PlaylistResponse> findPlaylistsByContributor(String contributorId, Pageable pageable) {
        return playlistRepository
                .findByLeadContributor_Id(IdGenerator.parse(contributorId), pageable)
                .map(playlistMapper::toResponse);
    }

    @CircuitBreaker(name = "db")
    public NominationResponse nominateSong(String playlistId, NominateSongRequest request) {
        Playlist playlist = requirePlaylist(playlistId);
        if (playlist.getStatus() != PlaylistStatus.OPEN) {
            throw new IllegalStateException("Playlist is not open for nominations");
        }
        if (playlist.getDeadline() != null && Instant.now().isAfter(playlist.getDeadline())) {
            throw new IllegalStateException("Nomination deadline has passed");
        }
        long songDbId = IdGenerator.parse(request.songId());
        long contributorDbId = IdGenerator.parse(request.contributorId());
        if (!songRepository.existsById(songDbId)) {
            throw new EntityNotFoundException("Song not found: " + request.songId());
        }
        if (!contributorRepository.existsById(contributorDbId)) {
            throw new EntityNotFoundException("Contributor not found: " + request.contributorId());
        }
        if (nominationRepository.existsByPlaylist_IdAndSong_Id(playlist.getId(), songDbId)) {
            throw new IllegalStateException("Song already nominated to this playlist");
        }
        Song song = songRepository.getReferenceById(songDbId);
        Contributor contributor = contributorRepository.getReferenceById(contributorDbId);
        return nominationMapper.toResponse(
                nominationRepository.save(new Nomination(null, null, playlist, song, contributor, null)));
    }

    @CircuitBreaker(name = "db")
    public NominationResponse approveNomination(String nominationId, String reviewerId) {
        return reviewNomination(nominationId, reviewerId, NominationStatus.APPROVED);
    }

    @CircuitBreaker(name = "db")
    public NominationResponse declineNomination(String nominationId, String reviewerId) {
        return reviewNomination(nominationId, reviewerId, NominationStatus.DECLINED);
    }

    @CircuitBreaker(name = "db")
    public PlaylistResponse startGuessing(String playlistId, String contributorId) {
        Playlist playlist = requirePlaylist(playlistId);
        if (playlist.getStatus() != PlaylistStatus.OPEN) {
            throw new IllegalStateException("Only open playlists can start guessing");
        }
        if (!Objects.equals(IdGenerator.parse(contributorId), playlist.getLeadContributor().getId())) {
            throw new IllegalStateException("Only the lead contributor can start guessing");
        }
        boolean deadlinePassed = playlist.getDeadline() == null || Instant.now().isAfter(playlist.getDeadline());
        boolean noPending = nominationRepository.findByPlaylist_IdAndStatus(playlist.getId(), NominationStatus.PENDING).isEmpty();
        if (!deadlinePassed && !noPending) {
            throw new IllegalStateException("Deadline has not passed and there are still pending nominations");
        }
        nominationRepository.declinePendingByPlaylistId(playlist.getId());
        playlist.setGuessingDeadline(Instant.now().plus(7, ChronoUnit.DAYS));
        playlist.setStatus(PlaylistStatus.GUESSING);
        return playlistMapper.toResponse(playlistRepository.save(playlist));
    }

    @CircuitBreaker(name = "db")
    public void submitGuesses(String playlistId, String contributorId, java.util.List<SubmitGuessesRequest.GuessItem> guessItems) {
        Playlist playlist = requirePlaylist(playlistId);
        if (playlist.getStatus() != PlaylistStatus.GUESSING) {
            throw new IllegalStateException("Playlist is not in guessing phase");
        }
        long contributorDbId = IdGenerator.parse(contributorId);
        if (!contributorRepository.existsById(contributorDbId)) {
            throw new EntityNotFoundException("Contributor not found: " + contributorId);
        }
        Contributor guesser = contributorRepository.getReferenceById(contributorDbId);
        guessRepository.deleteByPlaylistAndGuesser(playlist.getId(), contributorDbId);
        for (SubmitGuessesRequest.GuessItem item : guessItems) {
            long nominationDbId = IdGenerator.parse(item.nominationId());
            Nomination nomination = nominationRepository.findById(nominationDbId)
                    .orElseThrow(() -> new EntityNotFoundException("Nomination not found: " + item.nominationId()));
            Contributor guessedContributor = contributorRepository.getReferenceById(IdGenerator.parse(item.guessedContributorId()));
            guessRepository.save(new Guess(null, null, playlist, nomination, guesser, guessedContributor));
        }
        if (!guessSubmissionRepository.existsByPlaylist_IdAndContributor_Id(playlist.getId(), contributorDbId)) {
            guessSubmissionRepository.save(new GuessSubmission(null, null, playlist, guesser));
        }
    }

    @CircuitBreaker(name = "db")
    public PlaylistResponse publishPlaylist(String playlistId, String contributorId) {
        Playlist playlist = requirePlaylist(playlistId);
        if (playlist.getStatus() != PlaylistStatus.GUESSING) {
            throw new IllegalStateException("Only playlists in the guessing phase can be published");
        }
        if (!Objects.equals(IdGenerator.parse(contributorId), playlist.getLeadContributor().getId())) {
            throw new IllegalStateException("Only the lead contributor can publish the playlist");
        }
        playlist.setStatus(PlaylistStatus.PUBLISHED);
        PlaylistResponse response = playlistMapper.toResponse(playlistRepository.save(playlist));
        saveRankings(playlist);
        return response;
    }

    private void saveRankings(Playlist playlist) {
        List<Nomination> approved = nominationRepository.findByPlaylist_IdAndStatus(
                playlist.getId(), NominationStatus.APPROVED);
        List<Guess> allGuesses = guessRepository.findByPlaylist_Id(playlist.getId());

        Map<Long, Contributor> guessersById = allGuesses.stream()
                .collect(Collectors.toMap(
                        g -> g.getGuesser().getId(),
                        Guess::getGuesser,
                        (a, b) -> a));

        record Stats(Contributor contributor, int correct, int total) {}

        Map<Long, Long> approvedNominators = approved.stream()
                .collect(Collectors.toMap(Nomination::getId, n -> n.getNominatedBy().getId()));

        List<Stats> stats = allGuesses.stream()
                .collect(Collectors.groupingBy(g -> g.getGuesser().getId()))
                .entrySet().stream()
                .map(e -> {
                    List<Guess> guesses = e.getValue();
                    int correct = (int) guesses.stream()
                            .filter(g -> {
                                Long nominatorId = approvedNominators.get(g.getNomination().getId());
                                return nominatorId != null
                                        && nominatorId.equals(g.getGuessedContributor().getId());
                            })
                            .count();
                    return new Stats(guessersById.get(e.getKey()), correct, guesses.size());
                })
                .sorted(Comparator.comparingInt(Stats::correct).reversed())
                .toList();

        int rank = 1;
        for (int i = 0; i < stats.size(); i++) {
            if (i > 0 && stats.get(i).correct() < stats.get(i - 1).correct()) {
                rank = i + 1;
            }
            playlistRankingRepository.save(new PlaylistRanking(
                    null, null, playlist, stats.get(i).contributor(),
                    rank, stats.get(i).correct(), stats.get(i).total()));
        }
    }

    @CircuitBreaker(name = "db")
    @Transactional(readOnly = true)
    public List<RankingResponse> getRankings() {
        return playlistRankingRepository.findAllWithDetails().stream()
                .map(r -> new RankingResponse(
                        IdGenerator.format("play", r.getPlaylist().getId()),
                        r.getPlaylist().getName(),
                        IdGenerator.format("cont", r.getContributor().getId()),
                        r.getContributor().getName(),
                        r.getContributor().getAvatarUrl(),
                        r.getRankPosition(),
                        r.getCorrectGuesses(),
                        r.getTotalGuesses()))
                .toList();
    }

    @CircuitBreaker(name = "db")
    public void submitRatings(String playlistId, SubmitRatingsRequest request) {
        Playlist playlist = requirePlaylist(playlistId);
        if (playlist.getStatus() != PlaylistStatus.PUBLISHED) {
            throw new IllegalStateException("Ratings can only be submitted for published playlists");
        }
        if (playlist.getRatingType() == null) {
            throw new IllegalStateException("This playlist has no rating type configured");
        }
        long contributorDbId = IdGenerator.parse(request.contributorId());
        if (!contributorRepository.existsById(contributorDbId)) {
            throw new EntityNotFoundException("Contributor not found: " + request.contributorId());
        }
        validateRatings(playlist.getRatingType(), request.ratings());
        Contributor contributor = contributorRepository.getReferenceById(contributorDbId);
        songRatingRepository.deleteByPlaylistAndContributor(playlist.getId(), contributorDbId);
        for (var item : request.ratings()) {
            long nomDbId = IdGenerator.parse(item.nominationId());
            Nomination nomination = nominationRepository.findById(nomDbId)
                    .orElseThrow(() -> new EntityNotFoundException("Nomination not found: " + item.nominationId()));
            if (nomination.getNominatedBy().getId().equals(contributorDbId)) {
                throw new IllegalStateException("Cannot rate your own nomination");
            }
            songRatingRepository.save(new SongRating(null, null, playlist, contributor, nomination, item.points()));
        }
    }

    private void validateRatings(RatingType type, List<SubmitRatingsRequest.RatingItem> items) {
        var pointsList = items.stream().map(SubmitRatingsRequest.RatingItem::points).sorted().toList();
        var expected = switch (type) {
            case LINEAR -> List.of(1, 2, 3);
            case FIBONACCI -> List.of(5, 8, 13);
            case BEST_SONG -> List.of(1);
        };
        if (!pointsList.equals(expected)) {
            throw new IllegalStateException("Invalid rating points for " + type + ": expected " + expected);
        }
    }

    @CircuitBreaker(name = "db")
    @Transactional(readOnly = true)
    public List<SongRatingResponse> getSongRatings(String playlistId) {
        return songRatingRepository.findByPlaylistWithDetails(IdGenerator.parse(playlistId)).stream()
                .map(r -> new SongRatingResponse(
                        IdGenerator.format("nom", r.getNomination().getId()),
                        IdGenerator.format("cont", r.getContributor().getId()),
                        r.getContributor().getName(),
                        r.getPoints()))
                .toList();
    }

    @CircuitBreaker(name = "db")
    @Transactional(readOnly = true)
    public Page<NominationResponse> findNominations(String playlistId, Pageable pageable) {
        return nominationRepository
                .findByPlaylist_Id(IdGenerator.parse(playlistId), pageable)
                .map(nominationMapper::toResponse);
    }

    @CircuitBreaker(name = "db")
    @Transactional(readOnly = true)
    public List<SongNominationResponse> findNominationsBySong(String songId) {
        return nominationRepository.findBySong_Id(IdGenerator.parse(songId)).stream()
                .map(n -> new SongNominationResponse(
                        IdGenerator.format("nom",  n.getId()),
                        IdGenerator.format("play", n.getPlaylist().getId()),
                        n.getPlaylist().getName(),
                        IdGenerator.format("cont", n.getNominatedBy().getId()),
                        n.getNominatedBy().getName(),
                        n.getStatus()))
                .toList();
    }

    @CircuitBreaker(name = "db")
    @Transactional(readOnly = true)
    public List<GuessResponse> getGuesses(String playlistId) {
        return guessRepository.findByPlaylist_Id(IdGenerator.parse(playlistId))
                .stream()
                .map(g -> new GuessResponse(
                        IdGenerator.format("nom", g.getNomination().getId()),
                        IdGenerator.format("cont", g.getGuesser().getId()),
                        IdGenerator.format("cont", g.getGuessedContributor().getId())
                ))
                .toList();
    }

    private NominationResponse reviewNomination(String nominationId, String reviewerId, NominationStatus newStatus) {
        Nomination nomination = nominationRepository.findById(IdGenerator.parse(nominationId))
                .orElseThrow(() -> new EntityNotFoundException("Nomination not found: " + nominationId));
        if (nomination.getStatus() != NominationStatus.PENDING) {
            throw new IllegalStateException("Nomination is not pending: " + nomination.getStatus());
        }
        if (!Objects.equals(IdGenerator.parse(reviewerId), nomination.getPlaylist().getLeadContributor().getId())) {
            throw new IllegalStateException("Only the lead contributor can review nominations");
        }
        nomination.setStatus(newStatus);
        return nominationMapper.toResponse(nominationRepository.save(nomination));
    }

    private Playlist requirePlaylist(String playlistId) {
        return playlistRepository.findById(IdGenerator.parse(playlistId))
                .orElseThrow(() -> new EntityNotFoundException("Playlist not found: " + playlistId));
    }
}
