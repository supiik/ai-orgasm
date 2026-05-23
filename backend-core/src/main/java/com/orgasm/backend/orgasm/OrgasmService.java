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
import com.orgasm.backend.playlist.Playlist;
import com.orgasm.backend.playlist.PlaylistMapper;
import com.orgasm.backend.playlist.PlaylistRepository;
import com.orgasm.backend.playlist.PlaylistResponse;
import com.orgasm.backend.playlist.PlaylistStatus;
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
import java.util.Objects;

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
        return playlistMapper.toResponse(playlistRepository.save(playlist));
    }

    @CircuitBreaker(name = "db")
    @Transactional(readOnly = true)
    public Page<NominationResponse> findNominations(String playlistId, Pageable pageable) {
        return nominationRepository
                .findByPlaylist_Id(IdGenerator.parse(playlistId), pageable)
                .map(nominationMapper::toResponse);
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
