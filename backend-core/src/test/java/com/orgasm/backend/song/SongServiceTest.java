package com.orgasm.backend.song;

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
class SongServiceTest {

    @Mock SongRepository repository;
    @Mock SongMapper mapper;
    @InjectMocks SongService service;

    static SongResponse response(Long id, String artist, String name) {
        return new SongResponse(id, artist, name, null, null, 0L, Instant.EPOCH, Instant.EPOCH);
    }

    @Test
    void create_savesAndReturnsResponse() {
        var request = new CreateSongRequest("Radiohead", "Creep", "Pablo Honey", 1993);
        var entity = new Song(null, "Radiohead", "Creep", "Pablo Honey", 1993);
        var saved = new Song(1L, "Radiohead", "Creep", "Pablo Honey", 1993);
        var expected = response(1L, "Radiohead", "Creep");

        when(mapper.toEntity(request)).thenReturn(entity);
        when(repository.save(entity)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(expected);

        assertThat(service.create(request)).isEqualTo(expected);
    }

    @Test
    void create_savesAndReturnsResponse_viaBuilder() {
        var entity = new Song(null, "Radiohead", "Creep", null, null);
        var saved = new Song(1L, "Radiohead", "Creep", null, null);
        var expected = response(1L, "Radiohead", "Creep");

        when(mapper.toEntity(any(CreateSongRequest.class))).thenReturn(entity);
        when(repository.save(entity)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(expected);

        assertThat(service.create(b -> b.artist("Radiohead").name("Creep"))).isEqualTo(expected);
    }

    @Test
    void findById_returnsResponse_whenExists() {
        var entity = new Song(1L, "Radiohead", "Creep", null, null);
        var expected = response(1L, "Radiohead", "Creep");
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
        var entity = new Song(1L, "Artist", "Track", null, null);
        var mapped = response(1L, "Artist", "Track");
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(entity)));
        when(mapper.toResponse(entity)).thenReturn(mapped);

        Page<SongResponse> result = service.findAll(FindSongsRequest.builder().build(), Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(mapped);
    }

    @Test
    void findAll_returnsMappedPage_whenNameIsBlank() {
        var entity = new Song(1L, "Artist", "Track", null, null);
        var mapped = response(1L, "Artist", "Track");
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(entity)));
        when(mapper.toResponse(entity)).thenReturn(mapped);

        Page<SongResponse> result = service.findAll(new FindSongsRequest("  "), Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(mapped);
    }

    @Test
    void findAll_filtersBy_name() {
        var entity = new Song(1L, "Radiohead", "Creep", null, null);
        var mapped = response(1L, "Radiohead", "Creep");
        when(repository.findByNameContainingIgnoreCase(eq("cree"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));
        when(mapper.toResponse(entity)).thenReturn(mapped);

        Page<SongResponse> result = service.findAll(new FindSongsRequest("cree"), Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(mapped);
    }

    @Test
    void findAll_filtersBy_name_viaBuilder() {
        var entity = new Song(1L, "Radiohead", "Creep", null, null);
        var mapped = response(1L, "Radiohead", "Creep");
        when(repository.findByNameContainingIgnoreCase(eq("cree"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));
        when(mapper.toResponse(entity)).thenReturn(mapped);

        Page<SongResponse> result = service.findAll(b -> b.name("cree"), Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(mapped);
    }

    @Test
    void update_appliesMappingAndReturnsResponse() {
        var request = new UpdateSongRequest("New Artist", "New Name", null, null);
        var existing = new Song(1L, "Old Artist", "Old Name", null, null);
        var saved = new Song(1L, "New Artist", "New Name", null, null);
        var expected = response(1L, "New Artist", "New Name");

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(expected);

        assertThat(service.update(1L, request)).isEqualTo(expected);
        verify(mapper).updateEntity(request, existing);
    }

    @Test
    void update_appliesMappingAndReturnsResponse_viaBuilder() {
        var existing = new Song(1L, "Old Artist", "Old Name", null, null);
        var saved = new Song(1L, "New Artist", "New Name", null, null);
        var expected = response(1L, "New Artist", "New Name");

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(expected);

        assertThat(service.update(1L, b -> b.artist("New Artist").name("New Name"))).isEqualTo(expected);
        verify(mapper).updateEntity(any(UpdateSongRequest.class), eq(existing));
    }

    @Test
    void update_throwsNotFound_whenMissing() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, new UpdateSongRequest("X", "Y", null, null)))
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
