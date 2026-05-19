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

    static SongResponse response(String id, String artist, String name) {
        return new SongResponse(id, artist, name, null, null, 0L, Instant.EPOCH, Instant.EPOCH);
    }

    @Test
    void create_savesAndReturnsResponse() {
        var request = new CreateSongRequest("Radiohead", "Creep", "Pablo Honey", 1993);
        var entity = new Song(null, "Radiohead", "Creep", "Pablo Honey", 1993);
        var saved = new Song("song-0001", "Radiohead", "Creep", "Pablo Honey", 1993);
        var expected = response("song-0001", "Radiohead", "Creep");

        when(mapper.toEntity(request)).thenReturn(entity);
        when(repository.save(entity)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(expected);

        assertThat(service.create(request)).isEqualTo(expected);
    }

    @Test
    void create_savesAndReturnsResponse_viaBuilder() {
        var entity = new Song(null, "Radiohead", "Creep", null, null);
        var saved = new Song("song-0001", "Radiohead", "Creep", null, null);
        var expected = response("song-0001", "Radiohead", "Creep");

        when(mapper.toEntity(any(CreateSongRequest.class))).thenReturn(entity);
        when(repository.save(entity)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(expected);

        assertThat(service.create(b -> b.artist("Radiohead").name("Creep"))).isEqualTo(expected);
    }

    @Test
    void findById_returnsResponse_whenExists() {
        var entity = new Song("song-0001", "Radiohead", "Creep", null, null);
        var expected = response("song-0001", "Radiohead", "Creep");
        when(repository.findById("song-0001")).thenReturn(Optional.of(entity));
        when(mapper.toResponse(entity)).thenReturn(expected);

        assertThat(service.findById("song-0001")).contains(expected);
    }

    @Test
    void findById_returnsEmpty_whenNotFound() {
        when(repository.findById("song-9999")).thenReturn(Optional.empty());

        assertThat(service.findById("song-9999")).isEmpty();
    }

    @Test
    void findAll_returnsMappedPage_whenNameIsNull() {
        var entity = new Song("song-0001", "Artist", "Track", null, null);
        var mapped = response("song-0001", "Artist", "Track");
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(entity)));
        when(mapper.toResponse(entity)).thenReturn(mapped);

        Page<SongResponse> result = service.findAll(FindSongsRequest.builder().build(), Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(mapped);
    }

    @Test
    void findAll_returnsMappedPage_whenNameIsBlank() {
        var entity = new Song("song-0001", "Artist", "Track", null, null);
        var mapped = response("song-0001", "Artist", "Track");
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(entity)));
        when(mapper.toResponse(entity)).thenReturn(mapped);

        Page<SongResponse> result = service.findAll(new FindSongsRequest("  "), Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(mapped);
    }

    @Test
    void findAll_filtersBy_name() {
        var entity = new Song("song-0001", "Radiohead", "Creep", null, null);
        var mapped = response("song-0001", "Radiohead", "Creep");
        when(repository.findByNameContainingIgnoreCase(eq("cree"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));
        when(mapper.toResponse(entity)).thenReturn(mapped);

        Page<SongResponse> result = service.findAll(new FindSongsRequest("cree"), Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(mapped);
    }

    @Test
    void findAll_filtersBy_name_viaBuilder() {
        var entity = new Song("song-0001", "Radiohead", "Creep", null, null);
        var mapped = response("song-0001", "Radiohead", "Creep");
        when(repository.findByNameContainingIgnoreCase(eq("cree"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));
        when(mapper.toResponse(entity)).thenReturn(mapped);

        Page<SongResponse> result = service.findAll(b -> b.name("cree"), Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(mapped);
    }

    @Test
    void update_appliesMappingAndReturnsResponse() {
        var request = new UpdateSongRequest("New Artist", "New Name", null, null);
        var existing = new Song("song-0001", "Old Artist", "Old Name", null, null);
        var saved = new Song("song-0001", "New Artist", "New Name", null, null);
        var expected = response("song-0001", "New Artist", "New Name");

        when(repository.findById("song-0001")).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(expected);

        assertThat(service.update("song-0001", request)).isEqualTo(expected);
        verify(mapper).updateEntity(request, existing);
    }

    @Test
    void update_appliesMappingAndReturnsResponse_viaBuilder() {
        var existing = new Song("song-0001", "Old Artist", "Old Name", null, null);
        var saved = new Song("song-0001", "New Artist", "New Name", null, null);
        var expected = response("song-0001", "New Artist", "New Name");

        when(repository.findById("song-0001")).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(expected);

        assertThat(service.update("song-0001", b -> b.artist("New Artist").name("New Name"))).isEqualTo(expected);
        verify(mapper).updateEntity(any(UpdateSongRequest.class), eq(existing));
    }

    @Test
    void update_throwsNotFound_whenMissing() {
        when(repository.findById("song-9999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("song-9999", new UpdateSongRequest("X", "Y", null, null)))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("song-9999");
    }

    @Test
    void delete_softDeletes_whenExists() {
        when(repository.softDeleteById(eq("song-0001"), any())).thenReturn(1);

        service.delete("song-0001");

        verify(repository).softDeleteById(eq("song-0001"), any());
    }

    @Test
    void delete_throwsNotFound_whenMissing() {
        when(repository.softDeleteById(eq("song-9999"), any())).thenReturn(0);

        assertThatThrownBy(() -> service.delete("song-9999"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("song-9999");
    }
}
