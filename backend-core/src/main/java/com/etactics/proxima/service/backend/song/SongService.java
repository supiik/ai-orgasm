package com.etactics.proxima.service.backend.song;

import com.etactics.proxima.service.backend.domain.IdGenerator;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
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

    @CircuitBreaker(name = "db")
    public SongResponse create(CreateSongRequest request) {
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }

    @CircuitBreaker(name = "db")
    public SongResponse create(UnaryOperator<CreateSongRequest.CreateSongRequestBuilder> customizer) {
        return create(customizer.apply(CreateSongRequest.builder()).build());
    }

    @CircuitBreaker(name = "db")
    @Transactional(readOnly = true)
    public Optional<SongResponse> findById(String id) {
        return repository.findById(IdGenerator.parse(id)).map(mapper::toResponse);
    }

    @CircuitBreaker(name = "db")
    @Transactional(readOnly = true)
    public Page<SongResponse> findAll(FindSongsRequest request, Pageable pageable) {
        if (request.name() == null || request.name().isBlank()) {
            return repository.findAll(pageable).map(mapper::toResponse);
        }
        return repository.findByNameContainingIgnoreCase(request.name(), pageable).map(mapper::toResponse);
    }

    @CircuitBreaker(name = "db")
    @Transactional(readOnly = true)
    public Page<SongResponse> findAll(
            UnaryOperator<FindSongsRequest.FindSongsRequestBuilder> customizer, Pageable pageable) {
        return findAll(customizer.apply(FindSongsRequest.builder()).build(), pageable);
    }

    @CircuitBreaker(name = "db")
    public SongResponse update(String id, UpdateSongRequest request) {
        Song existing = requireById(id);
        mapper.updateEntity(request, existing);
        return mapper.toResponse(repository.save(existing));
    }

    @CircuitBreaker(name = "db")
    public SongResponse update(String id, UnaryOperator<UpdateSongRequest.UpdateSongRequestBuilder> customizer) {
        return update(id, customizer.apply(UpdateSongRequest.builder()).build());
    }

    @CircuitBreaker(name = "db")
    public void delete(String id) {
        if (repository.softDeleteById(IdGenerator.parse(id), Instant.now()) == 0) {
            throw new EntityNotFoundException("Song not found: " + id);
        }
    }

    private Song requireById(String id) {
        return repository.findById(IdGenerator.parse(id))
                .orElseThrow(() -> new EntityNotFoundException("Song not found: " + id));
    }
}
