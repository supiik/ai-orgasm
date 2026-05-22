package com.pi2.anchor.backend.sample;

import com.pi2.anchor.backend.domain.IdGenerator;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SampleServiceTest {

    @Mock SampleRepository repository;
    @Mock SampleMapper mapper;
    @InjectMocks SampleService service;

    static final long DB_ID = 1L;
    static final String USER_ID = IdGenerator.format("smpl", DB_ID);

    static SampleResponse response(String id, String name) {
        return new SampleResponse(id, name, null, null, 0, 0L, 0.0, null, true,
                null, null, SampleStatus.DRAFT, null, 0L, Instant.EPOCH, Instant.EPOCH);
    }

    static CreateSampleRequest createRequest() {
        return new CreateSampleRequest("Widget", "desc", null, 5, 100L, 4.5,
                new BigDecimal("9.99"), true, LocalDate.of(2000, 1, 1),
                LocalDateTime.of(2024, 6, 1, 10, 0), SampleStatus.DRAFT, null);
    }

    static Sample entity(Long id) {
        return new Sample(id, null, "Widget", "desc", null, 5, 100L, 4.5,
                new BigDecimal("9.99"), true, LocalDate.of(2000, 1, 1),
                LocalDateTime.of(2024, 6, 1, 10, 0), SampleStatus.DRAFT, null);
    }

    @Test
    void create_savesAndReturnsResponse() {
        var request = createRequest();
        var unsaved = entity(null);
        var saved = entity(DB_ID);
        var expected = response(USER_ID, "Widget");

        when(mapper.toEntity(request)).thenReturn(unsaved);
        when(repository.save(unsaved)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(expected);

        assertThat(service.create(request)).isEqualTo(expected);
    }

    @Test
    void create_savesAndReturnsResponse_viaBuilder() {
        var unsaved = entity(null);
        var saved = entity(DB_ID);
        var expected = response(USER_ID, "Widget");

        when(mapper.toEntity(any(CreateSampleRequest.class))).thenReturn(unsaved);
        when(repository.save(unsaved)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(expected);

        assertThat(service.create(b -> b.name("Widget").status(SampleStatus.DRAFT))).isEqualTo(expected);
    }

    @Test
    void findById_returnsResponse_whenExists() {
        var saved = entity(DB_ID);
        var expected = response(USER_ID, "Widget");
        when(repository.findById(DB_ID)).thenReturn(Optional.of(saved));
        when(mapper.toResponse(saved)).thenReturn(expected);

        assertThat(service.findById(USER_ID)).contains(expected);
    }

    @Test
    void findById_returnsEmpty_whenNotFound() {
        when(repository.findById(DB_ID)).thenReturn(Optional.empty());
        assertThat(service.findById(USER_ID)).isEmpty();
    }

    @Test
    void findAll_returnsMappedPage_noFilters() {
        var saved = entity(DB_ID);
        var mapped = response(USER_ID, "Widget");
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(saved)));
        when(mapper.toResponse(saved)).thenReturn(mapped);

        Page<SampleResponse> result = service.findAll(FindSamplesRequest.builder().build(), Pageable.unpaged());
        assertThat(result.getContent()).containsExactly(mapped);
    }

    @Test
    void findAll_filtersByName() {
        var saved = entity(DB_ID);
        var mapped = response(USER_ID, "Widget");
        when(repository.findByNameContainingIgnoreCase(eq("wid"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(saved)));
        when(mapper.toResponse(saved)).thenReturn(mapped);

        Page<SampleResponse> result = service.findAll(new FindSamplesRequest("wid", null), Pageable.unpaged());
        assertThat(result.getContent()).containsExactly(mapped);
    }

    @Test
    void findAll_filtersByStatus() {
        var saved = entity(DB_ID);
        var mapped = response(USER_ID, "Widget");
        when(repository.findByStatus(eq(SampleStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(saved)));
        when(mapper.toResponse(saved)).thenReturn(mapped);

        Page<SampleResponse> result = service.findAll(new FindSamplesRequest(null, SampleStatus.ACTIVE), Pageable.unpaged());
        assertThat(result.getContent()).containsExactly(mapped);
    }

    @Test
    void update_appliesMappingAndReturnsResponse() {
        var request = new UpdateSampleRequest("New Name", null, null, null, null, null, null, null, null, null, SampleStatus.ACTIVE, null);
        var existing = entity(DB_ID);
        var saved = entity(DB_ID);
        var expected = response(USER_ID, "New Name");

        when(repository.findById(DB_ID)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(expected);

        assertThat(service.update(USER_ID, request)).isEqualTo(expected);
        verify(mapper).updateEntity(request, existing);
    }

    @Test
    void update_throwsNotFound_whenMissing() {
        when(repository.findById(DB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(USER_ID,
                new UpdateSampleRequest("X", null, null, null, null, null, null, null, null, null, null, null)))
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
