package com.etactics.proxima.sal.backend.orgasm;

import com.etactics.proxima.sal.backend.contributor.Contributor;
import com.etactics.proxima.sal.backend.contributor.ContributorRepository;
import com.etactics.proxima.sal.backend.domain.IdGenerator;
import com.etactics.proxima.sal.backend.nomination.Nomination;
import com.etactics.proxima.sal.backend.nomination.NominationMapper;
import com.etactics.proxima.sal.backend.nomination.NominationRepository;
import com.etactics.proxima.sal.backend.nomination.NominationResponse;
import com.etactics.proxima.sal.backend.nomination.NominationStatus;
import com.etactics.proxima.sal.backend.playlist.Playlist;
import com.etactics.proxima.sal.backend.playlist.PlaylistMapper;
import com.etactics.proxima.sal.backend.playlist.PlaylistRepository;
import com.etactics.proxima.sal.backend.playlist.PlaylistResponse;
import com.etactics.proxima.sal.backend.playlist.PlaylistStatus;
import com.etactics.proxima.sal.backend.song.Song;
import com.etactics.proxima.sal.backend.song.SongRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

@Service
@Transactional("appTransactionManager")
@RequiredArgsConstructor
public class OrgasmService {

    private final PlaylistRepository playlistRepository;
    private final ContributorRepository contributorRepository;
    private final SongRepository songRepository;
    private final NominationRepository nominationRepository;
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
    public PlaylistResponse publishPlaylist(String playlistId, String contributorId) {
        Playlist playlist = requirePlaylist(playlistId);
        if (playlist.getStatus() != PlaylistStatus.OPEN) {
            throw new IllegalStateException("Only open playlists can be published");
        }
        if (!Objects.equals(IdGenerator.parse(contributorId), playlist.getLeadContributor().getId())) {
            throw new IllegalStateException("Only the lead contributor can publish the playlist");
        }
        if (playlist.getDeadline() != null && Instant.now().isBefore(playlist.getDeadline())) {
            throw new IllegalStateException("Playlist deadline has not yet passed");
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
