package com.pi2.anchor.backend.sample;

import com.pi2.anchor.backend.domain.IdGenerator;
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
public class SampleService {

    private final SampleRepository repository;
    private final SampleMapper mapper;

    @CircuitBreaker(name = "db")
    public SampleResponse create(CreateSampleRequest request) {
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }

    @CircuitBreaker(name = "db")
    public SampleResponse create(UnaryOperator<CreateSampleRequest.CreateSampleRequestBuilder> customizer) {
        return create(customizer.apply(CreateSampleRequest.builder()).build());
    }

    @CircuitBreaker(name = "db")
    @Transactional(readOnly = true)
    public Optional<SampleResponse> findById(String id) {
        return repository.findById(IdGenerator.parse(id)).map(mapper::toResponse);
    }

    @CircuitBreaker(name = "db")
    @Transactional(readOnly = true)
    public Page<SampleResponse> findAll(FindSamplesRequest request, Pageable pageable) {
        if (request.name() != null && !request.name().isBlank()) {
            return repository.findByNameContainingIgnoreCase(request.name(), pageable).map(mapper::toResponse);
        }
        if (request.status() != null) {
            return repository.findByStatus(request.status(), pageable).map(mapper::toResponse);
        }
        return repository.findAll(pageable).map(mapper::toResponse);
    }

    @CircuitBreaker(name = "db")
    @Transactional(readOnly = true)
    public Page<SampleResponse> findAll(
            UnaryOperator<FindSamplesRequest.FindSamplesRequestBuilder> customizer, Pageable pageable) {
        return findAll(customizer.apply(FindSamplesRequest.builder()).build(), pageable);
    }

    @CircuitBreaker(name = "db")
    public SampleResponse update(String id, UpdateSampleRequest request) {
        Sample existing = requireById(id);
        mapper.updateEntity(request, existing);
        return mapper.toResponse(repository.save(existing));
    }

    @CircuitBreaker(name = "db")
    public SampleResponse update(String id, UnaryOperator<UpdateSampleRequest.UpdateSampleRequestBuilder> customizer) {
        return update(id, customizer.apply(UpdateSampleRequest.builder()).build());
    }

    @CircuitBreaker(name = "db")
    public void delete(String id) {
        if (repository.softDeleteById(IdGenerator.parse(id), Instant.now()) == 0) {
            throw new EntityNotFoundException("Sample not found: " + id);
        }
    }

    private Sample requireById(String id) {
        return repository.findById(IdGenerator.parse(id))
                .orElseThrow(() -> new EntityNotFoundException("Sample not found: " + id));
    }
}
