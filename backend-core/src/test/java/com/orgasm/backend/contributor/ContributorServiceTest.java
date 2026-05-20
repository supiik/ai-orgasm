package com.orgasm.backend.contributor;

import com.orgasm.backend.domain.IdGenerator;
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

    static final long DB_ID = 1L;
    static final String USER_ID = IdGenerator.format("cont", DB_ID);

    static ContributorResponse response(String id, String name) {
        return new ContributorResponse(id, name, null, null, 0L, Instant.EPOCH, Instant.EPOCH);
    }

    @Test
    void create_savesAndReturnsResponse() {
        var request = new CreateContributorRequest("Alice", null, null);
        var entity = new Contributor(null, null, "Alice", null, null);
        var saved = new Contributor(DB_ID, null, "Alice", null, null);
        var expected = response(USER_ID, "Alice");

        when(mapper.toEntity(request)).thenReturn(entity);
        when(repository.save(entity)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(expected);

        assertThat(service.create(request)).isEqualTo(expected);
    }

    @Test
    void create_savesAndReturnsResponse_viaBuilder() {
        var entity = new Contributor(null, null, "Alice", null, null);
        var saved = new Contributor(DB_ID, null, "Alice", null, null);
        var expected = response(USER_ID, "Alice");

        when(mapper.toEntity(any(CreateContributorRequest.class))).thenReturn(entity);
        when(repository.save(entity)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(expected);

        assertThat(service.create(b -> b.name("Alice"))).isEqualTo(expected);
    }

    @Test
    void findById_returnsResponse_whenExists() {
        var entity = new Contributor(DB_ID, null, "Alice", null, null);
        var expected = response(USER_ID, "Alice");
        when(repository.findById(DB_ID)).thenReturn(Optional.of(entity));
        when(mapper.toResponse(entity)).thenReturn(expected);

        assertThat(service.findById(USER_ID)).contains(expected);
    }

    @Test
    void findById_returnsEmpty_whenNotFound() {
        when(repository.findById(DB_ID)).thenReturn(Optional.empty());

        assertThat(service.findById(USER_ID)).isEmpty();
    }

    @Test
    void findAll_returnsMappedPage_whenNameIsNull() {
        var entity = new Contributor(DB_ID, null, "Alice", null, null);
        var mapped = response(USER_ID, "Alice");
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(entity)));
        when(mapper.toResponse(entity)).thenReturn(mapped);

        Page<ContributorResponse> result = service.findAll(FindContributorsRequest.builder().build(), Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(mapped);
    }

    @Test
    void findAll_returnsMappedPage_whenNameIsBlank() {
        var entity = new Contributor(DB_ID, null, "Alice", null, null);
        var mapped = response(USER_ID, "Alice");
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(entity)));
        when(mapper.toResponse(entity)).thenReturn(mapped);

        Page<ContributorResponse> result = service.findAll(new FindContributorsRequest("  "), Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(mapped);
    }

    @Test
    void findAll_filtersBy_name() {
        var entity = new Contributor(DB_ID, null, "Alice", null, null);
        var mapped = response(USER_ID, "Alice");
        when(repository.findByNameContainingIgnoreCase(eq("ali"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));
        when(mapper.toResponse(entity)).thenReturn(mapped);

        Page<ContributorResponse> result = service.findAll(new FindContributorsRequest("ali"), Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(mapped);
    }

    @Test
    void findAll_filtersBy_name_viaBuilder() {
        var entity = new Contributor(DB_ID, null, "Alice", null, null);
        var mapped = response(USER_ID, "Alice");
        when(repository.findByNameContainingIgnoreCase(eq("ali"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));
        when(mapper.toResponse(entity)).thenReturn(mapped);

        Page<ContributorResponse> result = service.findAll(b -> b.name("ali"), Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(mapped);
    }

    @Test
    void update_appliesMappingAndReturnsResponse() {
        var request = new UpdateContributorRequest("Bob", null, null);
        var existing = new Contributor(DB_ID, null, "Alice", null, null);
        var saved = new Contributor(DB_ID, null, "Bob", null, null);
        var expected = response(USER_ID, "Bob");

        when(repository.findById(DB_ID)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(expected);

        assertThat(service.update(USER_ID, request)).isEqualTo(expected);
        verify(mapper).updateEntity(request, existing);
    }

    @Test
    void update_appliesMappingAndReturnsResponse_viaBuilder() {
        var existing = new Contributor(DB_ID, null, "Alice", null, null);
        var saved = new Contributor(DB_ID, null, "Bob", null, null);
        var expected = response(USER_ID, "Bob");

        when(repository.findById(DB_ID)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(expected);

        assertThat(service.update(USER_ID, b -> b.name("Bob"))).isEqualTo(expected);
        verify(mapper).updateEntity(any(UpdateContributorRequest.class), eq(existing));
    }

    @Test
    void update_throwsNotFound_whenMissing() {
        when(repository.findById(DB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(USER_ID, new UpdateContributorRequest("X", null, null)))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining(USER_ID);
    }

    @Test
    void delete_softDeletes_whenExists() {
        when(repository.softDeleteById(eq(DB_ID), any())).thenReturn(1);

        service.delete(USER_ID);

        verify(repository).softDeleteById(eq(DB_ID), any());
    }

    @Test
    void delete_throwsNotFound_whenMissing() {
        when(repository.softDeleteById(eq(DB_ID), any())).thenReturn(0);

        assertThatThrownBy(() -> service.delete(USER_ID))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining(USER_ID);
    }
}
