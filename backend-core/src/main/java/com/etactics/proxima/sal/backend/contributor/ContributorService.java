package com.etactics.proxima.sal.backend.contributor;

import com.etactics.proxima.sal.backend.domain.IdGenerator;
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
public class ContributorService {

    private final ContributorRepository repository;
    private final ContributorMapper mapper;

    @CircuitBreaker(name = "db")
    public ContributorResponse create(CreateContributorRequest request) {
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }

    @CircuitBreaker(name = "db")
    public ContributorResponse create(UnaryOperator<CreateContributorRequest.CreateContributorRequestBuilder> customizer) {
        return create(customizer.apply(CreateContributorRequest.builder()).build());
    }

    @CircuitBreaker(name = "db")
    @Transactional(readOnly = true)
    public Optional<ContributorResponse> findById(String id) {
        return repository.findById(IdGenerator.parse(id)).map(mapper::toResponse);
    }

    @CircuitBreaker(name = "db")
    @Transactional(readOnly = true)
    public Page<ContributorResponse> findAll(FindContributorsRequest request, Pageable pageable) {
        if (request.name() == null || request.name().isBlank()) {
            return repository.findAll(pageable).map(mapper::toResponse);
        }
        return repository.findByNameContainingIgnoreCase(request.name(), pageable).map(mapper::toResponse);
    }

    @CircuitBreaker(name = "db")
    @Transactional(readOnly = true)
    public Page<ContributorResponse> findAll(
            UnaryOperator<FindContributorsRequest.FindContributorsRequestBuilder> customizer, Pageable pageable) {
        return findAll(customizer.apply(FindContributorsRequest.builder()).build(), pageable);
    }

    @CircuitBreaker(name = "db")
    public ContributorResponse update(String id, UpdateContributorRequest request) {
        Contributor existing = requireById(id);
        mapper.updateEntity(request, existing);
        return mapper.toResponse(repository.save(existing));
    }

    @CircuitBreaker(name = "db")
    public ContributorResponse update(String id, UnaryOperator<UpdateContributorRequest.UpdateContributorRequestBuilder> customizer) {
        return update(id, customizer.apply(UpdateContributorRequest.builder()).build());
    }

    @CircuitBreaker(name = "db")
    public void delete(String id) {
        if (repository.softDeleteById(IdGenerator.parse(id), Instant.now()) == 0) {
            throw new EntityNotFoundException("Contributor not found: " + id);
        }
    }

    private Contributor requireById(String id) {
        return repository.findById(IdGenerator.parse(id))
                .orElseThrow(() -> new EntityNotFoundException("Contributor not found: " + id));
    }
}
