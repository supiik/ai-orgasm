package com.orgasm.backend.contributor;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContributorServiceTest {

    @Mock ContributorRepository repository;
    @Mock ContributorMapper mapper;
    @InjectMocks ContributorService service;

    static ContributorResponse response(Long id, String name) {
        return new ContributorResponse(id, name, null, null, 0L, Instant.EPOCH, Instant.EPOCH);
    }

    @Test
    void create_savesAndReturnsResponse() {
        var request = new CreateContributorRequest("Alice", null, null);
        var entity = new Contributor(null, "Alice", null, null);
        var saved = new Contributor(1L, "Alice", null, null);
        var expected = response(1L, "Alice");

        when(mapper.toEntity(request)).thenReturn(entity);
        when(repository.save(entity)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(expected);

        assertThat(service.create(request)).isEqualTo(expected);
    }

    @Test
    void create_savesAndReturnsResponse_viaBuilder() {
        var entity = new Contributor(null, "Alice", null, null);
        var saved = new Contributor(1L, "Alice", null, null);
        var expected = response(1L, "Alice");

        when(mapper.toEntity(any(CreateContributorRequest.class))).thenReturn(entity);
        when(repository.save(entity)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(expected);

        assertThat(service.create(b -> b.name("Alice"))).isEqualTo(expected);
    }

    @Test
    void findById_returnsResponse_whenExists() {
        var entity = new Contributor(1L, "Alice", null, null);
        var expected = response(1L, "Alice");
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(mapper.toResponse(entity)).thenReturn(expected);

        assertThat(service.findById(1L)).contains(expected);
    }

    @Test
    void findById_returnsEmpty_whenNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThat(service.findById(99L)).isEmpty();
    }

    @Test
    void findAll_returnsMappedPage_whenNameIsNull() {
        var entity = new Contributor(1L, "Alice", null, null);
        var mapped = response(1L, "Alice");
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(entity)));
        when(mapper.toResponse(entity)).thenReturn(mapped);

        Page<ContributorResponse> result = service.findAll(FindContributorsRequest.builder().build(), Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(mapped);
    }

    @Test
    void findAll_returnsMappedPage_whenNameIsBlank() {
        var entity = new Contributor(1L, "Alice", null, null);
        var mapped = response(1L, "Alice");
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(entity)));
        when(mapper.toResponse(entity)).thenReturn(mapped);

        Page<ContributorResponse> result = service.findAll(new FindContributorsRequest("  "), Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(mapped);
    }

    @Test
    void findAll_filtersBy_name() {
        var entity = new Contributor(1L, "Alice", null, null);
        var mapped = response(1L, "Alice");
        when(repository.findByNameContainingIgnoreCase(eq("ali"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));
        when(mapper.toResponse(entity)).thenReturn(mapped);

        Page<ContributorResponse> result = service.findAll(new FindContributorsRequest("ali"), Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(mapped);
    }

    @Test
    void findAll_filtersBy_name_viaBuilder() {
        var entity = new Contributor(1L, "Alice", null, null);
        var mapped = response(1L, "Alice");
        when(repository.findByNameContainingIgnoreCase(eq("ali"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));
        when(mapper.toResponse(entity)).thenReturn(mapped);

        Page<ContributorResponse> result = service.findAll(b -> b.name("ali"), Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(mapped);
    }

    @Test
    void update_appliesMappingAndReturnsResponse() {
        var request = new UpdateContributorRequest("Bob", null, null);
        var existing = new Contributor(1L, "Alice", null, null);
        var saved = new Contributor(1L, "Bob", null, null);
        var expected = response(1L, "Bob");

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(expected);

        assertThat(service.update(1L, request)).isEqualTo(expected);
        verify(mapper).updateEntity(request, existing);
    }

    @Test
    void update_appliesMappingAndReturnsResponse_viaBuilder() {
        var existing = new Contributor(1L, "Alice", null, null);
        var saved = new Contributor(1L, "Bob", null, null);
        var expected = response(1L, "Bob");

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(expected);

        assertThat(service.update(1L, b -> b.name("Bob"))).isEqualTo(expected);
        verify(mapper).updateEntity(any(UpdateContributorRequest.class), eq(existing));
    }

    @Test
    void update_throwsNotFound_whenMissing() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, new UpdateContributorRequest("X", null, null)))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void delete_softDeletes_whenExists() {
        when(repository.softDeleteById(eq(1L), any())).thenReturn(1);

        service.delete(1L);

        verify(repository).softDeleteById(eq(1L), any());
    }

    @Test
    void delete_throwsNotFound_whenMissing() {
        when(repository.softDeleteById(eq(99L), any())).thenReturn(0);

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
    }
}
