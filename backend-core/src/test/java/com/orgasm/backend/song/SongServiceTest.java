package com.orgasm.backend.song;

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
class SongServiceTest {

    @Mock SongRepository repository;
    @Mock SongMapper mapper;
    @InjectMocks SongService service;

    static final long DB_ID = 1L;
    static final String USER_ID = IdGenerator.format("song", DB_ID);

    static SongResponse response(String id, String artist, String name) {
        return new SongResponse(id, artist, name, null, null, null, 0L, Instant.EPOCH, Instant.EPOCH);
    }

    @Test
    void create_savesAndReturnsResponse() {
        var request = new CreateSongRequest("Radiohead", "Creep", "Pablo Honey", 1993, null);
        var entity = new Song(null, null, "Radiohead", "Creep", "Pablo Honey", 1993, null);
        var saved = new Song(DB_ID, null, "Radiohead", "Creep", "Pablo Honey", 1993, null);
        var expected = response(USER_ID, "Radiohead", "Creep");

        when(mapper.toEntity(request)).thenReturn(entity);
        when(repository.save(entity)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(expected);

        assertThat(service.create(request)).isEqualTo(expected);
    }

    @Test
    void create_savesAndReturnsResponse_viaBuilder() {
        var entity = new Song(null, null, "Radiohead", "Creep", null, null, null);
        var saved = new Song(DB_ID, null, "Radiohead", "Creep", null, null, null);
        var expected = response(USER_ID, "Radiohead", "Creep");

        when(mapper.toEntity(any(CreateSongRequest.class))).thenReturn(entity);
        when(repository.save(entity)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(expected);

        assertThat(service.create(b -> b.artist("Radiohead").name("Creep"))).isEqualTo(expected);
    }

    @Test
    void findById_returnsResponse_whenExists() {
        var entity = new Song(DB_ID, null, "Radiohead", "Creep", null, null, null);
        var expected = response(USER_ID, "Radiohead", "Creep");
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
        var entity = new Song(DB_ID, null, "Artist", "Track", null, null, null);
        var mapped = response(USER_ID, "Artist", "Track");
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(entity)));
        when(mapper.toResponse(entity)).thenReturn(mapped);

        Page<SongResponse> result = service.findAll(FindSongsRequest.builder().build(), Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(mapped);
    }

    @Test
    void findAll_returnsMappedPage_whenNameIsBlank() {
        var entity = new Song(DB_ID, null, "Artist", "Track", null, null, null);
        var mapped = response(USER_ID, "Artist", "Track");
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(entity)));
        when(mapper.toResponse(entity)).thenReturn(mapped);

        Page<SongResponse> result = service.findAll(new FindSongsRequest("  "), Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(mapped);
    }

    @Test
    void findAll_filtersBy_name() {
        var entity = new Song(DB_ID, null, "Radiohead", "Creep", null, null, null);
        var mapped = response(USER_ID, "Radiohead", "Creep");
        when(repository.findByNameContainingIgnoreCase(eq("cree"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));
        when(mapper.toResponse(entity)).thenReturn(mapped);

        Page<SongResponse> result = service.findAll(new FindSongsRequest("cree"), Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(mapped);
    }

    @Test
    void findAll_filtersBy_name_viaBuilder() {
        var entity = new Song(DB_ID, null, "Radiohead", "Creep", null, null, null);
        var mapped = response(USER_ID, "Radiohead", "Creep");
        when(repository.findByNameContainingIgnoreCase(eq("cree"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));
        when(mapper.toResponse(entity)).thenReturn(mapped);

        Page<SongResponse> result = service.findAll(b -> b.name("cree"), Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(mapped);
    }

    @Test
    void update_appliesMappingAndReturnsResponse() {
        var request = new UpdateSongRequest("New Artist", "New Name", null, null, null);
        var existing = new Song(DB_ID, null, "Old Artist", "Old Name", null, null, null);
        var saved = new Song(DB_ID, null, "New Artist", "New Name", null, null, null);
        var expected = response(USER_ID, "New Artist", "New Name");

        when(repository.findById(DB_ID)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(expected);

        assertThat(service.update(USER_ID, request)).isEqualTo(expected);
        verify(mapper).updateEntity(request, existing);
    }

    @Test
    void update_appliesMappingAndReturnsResponse_viaBuilder() {
        var existing = new Song(DB_ID, null, "Old Artist", "Old Name", null, null, null);
        var saved = new Song(DB_ID, null, "New Artist", "New Name", null, null, null);
        var expected = response(USER_ID, "New Artist", "New Name");

        when(repository.findById(DB_ID)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(expected);

        assertThat(service.update(USER_ID, b -> b.artist("New Artist").name("New Name"))).isEqualTo(expected);
        verify(mapper).updateEntity(any(UpdateSongRequest.class), eq(existing));
    }

    @Test
    void update_throwsNotFound_whenMissing() {
        when(repository.findById(DB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(USER_ID, new UpdateSongRequest("X", "Y", null, null, null)))
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
