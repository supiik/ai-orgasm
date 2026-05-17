package com.orgasm.backend.playlist;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@Transactional("appTransactionManager")
@RequiredArgsConstructor
@Slf4j
public class PlaylistService {

    private final PlaylistRepository repository;
    private final PlaylistMapper mapper;

    public PlaylistResponse create(CreatePlaylistRequest request) {
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }

    @Transactional(readOnly = true)
    public Optional<PlaylistResponse> findById(Long id) {
        return repository.findById(id).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<PlaylistResponse> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(mapper::toResponse);
    }

    public PlaylistResponse update(Long id, UpdatePlaylistRequest request) {
        Playlist existing = requireById(id);
        mapper.updateEntity(request, existing);
        return mapper.toResponse(repository.save(existing));
    }

    public void delete(Long id) {
        if (repository.softDeleteById(id, Instant.now()) == 0) {
            throw new EntityNotFoundException("Playlist not found: " + id);
        }
    }

    private Playlist requireById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Playlist not found: " + id));
    }
}
