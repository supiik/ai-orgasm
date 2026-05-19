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

    static ContributorResponse response(String id, String name) {
        return new ContributorResponse(id, name, null, null, 0L, Instant.EPOCH, Instant.EPOCH);
    }

    @Test
    void create_savesAndReturnsResponse() {
        var request = new CreateContributorRequest("Alice", null, null);
        var entity = new Contributor(null, "Alice", null, null);
        var saved = new Contributor("cont-0001", "Alice", null, null);
        var expected = response("cont-0001", "Alice");

        when(mapper.toEntity(request)).thenReturn(entity);
        when(repository.save(entity)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(expected);

        assertThat(service.create(request)).isEqualTo(expected);
    }

    @Test
    void create_savesAndReturnsResponse_viaBuilder() {
        var entity = new Contributor(null, "Alice", null, null);
        var saved = new Contributor("cont-0001", "Alice", null, null);
        var expected = response("cont-0001", "Alice");

        when(mapper.toEntity(any(CreateContributorRequest.class))).thenReturn(entity);
        when(repository.save(entity)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(expected);

        assertThat(service.create(b -> b.name("Alice"))).isEqualTo(expected);
    }

    @Test
    void findById_returnsResponse_whenExists() {
        var entity = new Contributor("cont-0001", "Alice", null, null);
        var expected = response("cont-0001", "Alice");
        when(repository.findById("cont-0001")).thenReturn(Optional.of(entity));
        when(mapper.toResponse(entity)).thenReturn(expected);

        assertThat(service.findById("cont-0001")).contains(expected);
    }

    @Test
    void findById_returnsEmpty_whenNotFound() {
        when(repository.findById("cont-9999")).thenReturn(Optional.empty());

        assertThat(service.findById("cont-9999")).isEmpty();
    }

    @Test
    void findAll_returnsMappedPage_whenNameIsNull() {
        var entity = new Contributor("cont-0001", "Alice", null, null);
        var mapped = response("cont-0001", "Alice");
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(entity)));
        when(mapper.toResponse(entity)).thenReturn(mapped);

        Page<ContributorResponse> result = service.findAll(FindContributorsRequest.builder().build(), Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(mapped);
    }

    @Test
    void findAll_returnsMappedPage_whenNameIsBlank() {
        var entity = new Contributor("cont-0001", "Alice", null, null);
        var mapped = response("cont-0001", "Alice");
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(entity)));
        when(mapper.toResponse(entity)).thenReturn(mapped);

        Page<ContributorResponse> result = service.findAll(new FindContributorsRequest("  "), Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(mapped);
    }

    @Test
    void findAll_filtersBy_name() {
        var entity = new Contributor("cont-0001", "Alice", null, null);
        var mapped = response("cont-0001", "Alice");
        when(repository.findByNameContainingIgnoreCase(eq("ali"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));
        when(mapper.toResponse(entity)).thenReturn(mapped);

        Page<ContributorResponse> result = service.findAll(new FindContributorsRequest("ali"), Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(mapped);
    }

    @Test
    void findAll_filtersBy_name_viaBuilder() {
        var entity = new Contributor("cont-0001", "Alice", null, null);
        var mapped = response("cont-0001", "Alice");
        when(repository.findByNameContainingIgnoreCase(eq("ali"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));
        when(mapper.toResponse(entity)).thenReturn(mapped);

        Page<ContributorResponse> result = service.findAll(b -> b.name("ali"), Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(mapped);
    }

    @Test
    void update_appliesMappingAndReturnsResponse() {
        var request = new UpdateContributorRequest("Bob", null, null);
        var existing = new Contributor("cont-0001", "Alice", null, null);
        var saved = new Contributor("cont-0001", "Bob", null, null);
        var expected = response("cont-0001", "Bob");

        when(repository.findById("cont-0001")).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(expected);

        assertThat(service.update("cont-0001", request)).isEqualTo(expected);
        verify(mapper).updateEntity(request, existing);
    }

    @Test
    void update_appliesMappingAndReturnsResponse_viaBuilder() {
        var existing = new Contributor("cont-0001", "Alice", null, null);
        var saved = new Contributor("cont-0001", "Bob", null, null);
        var expected = response("cont-0001", "Bob");

        when(repository.findById("cont-0001")).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(expected);

        assertThat(service.update("cont-0001", b -> b.name("Bob"))).isEqualTo(expected);
        verify(mapper).updateEntity(any(UpdateContributorRequest.class), eq(existing));
    }

    @Test
    void update_throwsNotFound_whenMissing() {
        when(repository.findById("cont-9999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("cont-9999", new UpdateContributorRequest("X", null, null)))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("cont-9999");
    }

    @Test
    void delete_softDeletes_whenExists() {
        when(repository.softDeleteById(eq("cont-0001"), any())).thenReturn(1);

        service.delete("cont-0001");

        verify(repository).softDeleteById(eq("cont-0001"), any());
    }

    @Test
    void delete_throwsNotFound_whenMissing() {
        when(repository.softDeleteById(eq("cont-9999"), any())).thenReturn(0);

        assertThatThrownBy(() -> service.delete("cont-9999"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("cont-9999");
    }
}
