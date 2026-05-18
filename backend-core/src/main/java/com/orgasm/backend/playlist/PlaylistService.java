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
import java.util.function.UnaryOperator;

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

    public PlaylistResponse create(UnaryOperator<CreatePlaylistRequest.CreatePlaylistRequestBuilder> customizer) {
        return create(customizer.apply(CreatePlaylistRequest.builder()).build());
    }

    @Transactional(readOnly = true)
    public Optional<PlaylistResponse> findById(String id) {
        return repository.findById(id).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<PlaylistResponse> findAll(FindPlaylistsRequest request, Pageable pageable) {
        if (request.name() == null || request.name().isBlank()) {
            return repository.findAll(pageable).map(mapper::toResponse);
        }
        return repository.findByNameContainingIgnoreCase(request.name(), pageable).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<PlaylistResponse> findAll(
            UnaryOperator<FindPlaylistsRequest.FindPlaylistsRequestBuilder> customizer, Pageable pageable) {
        return findAll(customizer.apply(FindPlaylistsRequest.builder()).build(), pageable);
    }

    public PlaylistResponse update(String id, UpdatePlaylistRequest request) {
        Playlist existing = requireById(id);
        mapper.updateEntity(request, existing);
        return mapper.toResponse(repository.save(existing));
    }

    public PlaylistResponse update(String id, UnaryOperator<UpdatePlaylistRequest.UpdatePlaylistRequestBuilder> customizer) {
        return update(id, customizer.apply(UpdatePlaylistRequest.builder()).build());
    }

    public void delete(String id) {
        if (repository.softDeleteById(id, Instant.now()) == 0) {
            throw new EntityNotFoundException("Playlist not found: " + id);
        }
    }

    private Playlist requireById(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Playlist not found: " + id));
    }
}
