package com.orgasm.backend.song;

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
public class SongService {

    private final SongRepository repository;
    private final SongMapper mapper;

    public SongResponse create(CreateSongRequest request) {
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }

    public SongResponse create(UnaryOperator<CreateSongRequest.CreateSongRequestBuilder> customizer) {
        return create(customizer.apply(CreateSongRequest.builder()).build());
    }

    @Transactional(readOnly = true)
    public Optional<SongResponse> findById(Long id) {
        return repository.findById(id).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<SongResponse> findAll(FindSongsRequest request, Pageable pageable) {
        if (request.name() == null || request.name().isBlank()) {
            return repository.findAll(pageable).map(mapper::toResponse);
        }
        return repository.findByNameContainingIgnoreCase(request.name(), pageable).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<SongResponse> findAll(
            UnaryOperator<FindSongsRequest.FindSongsRequestBuilder> customizer, Pageable pageable) {
        return findAll(customizer.apply(FindSongsRequest.builder()).build(), pageable);
    }

    public SongResponse update(Long id, UpdateSongRequest request) {
        Song existing = requireById(id);
        mapper.updateEntity(request, existing);
        return mapper.toResponse(repository.save(existing));
    }

    public SongResponse update(Long id, UnaryOperator<UpdateSongRequest.UpdateSongRequestBuilder> customizer) {
        return update(id, customizer.apply(UpdateSongRequest.builder()).build());
    }

    public void delete(Long id) {
        if (repository.softDeleteById(id, Instant.now()) == 0) {
            throw new EntityNotFoundException("Song not found: " + id);
        }
    }

    private Song requireById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Song not found: " + id));
    }
}
