package com.orgasm.backend.playlist;

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
class PlaylistServiceTest {

    @Mock PlaylistRepository repository;
    @Mock PlaylistMapper mapper;
    @InjectMocks PlaylistService service;

    static PlaylistResponse response(Long id, String name) {
        return new PlaylistResponse(id, name, null, PlaylistStatus.NEW, 0L, Instant.EPOCH, Instant.EPOCH);
    }

    @Test
    void create_savesAndReturnsResponse() {
        var request = new CreatePlaylistRequest("My Mix", "desc", null);
        var entity = new Playlist(null, "My Mix", "desc", null);
        var saved = new Playlist(1L, "My Mix", "desc", PlaylistStatus.NEW);
        var expected = response(1L, "My Mix");

        when(mapper.toEntity(request)).thenReturn(entity);
        when(repository.save(entity)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(expected);

        assertThat(service.create(request)).isEqualTo(expected);
    }

    @Test
    void create_savesAndReturnsResponse_viaBuilder() {
        var entity = new Playlist(null, "My Mix", "desc", null);
        var saved = new Playlist(1L, "My Mix", "desc", PlaylistStatus.NEW);
        var expected = response(1L, "My Mix");

        when(mapper.toEntity(any(CreatePlaylistRequest.class))).thenReturn(entity);
        when(repository.save(entity)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(expected);

        assertThat(service.create(b -> b.name("My Mix").description("desc"))).isEqualTo(expected);
    }

    @Test
    void findById_returnsResponse_whenExists() {
        var entity = new Playlist(1L, "My Mix", "desc", PlaylistStatus.NEW);
        var expected = response(1L, "My Mix");
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
    void findAll_returnsMappedPage() {
        var entity = new Playlist(1L, "A", null, PlaylistStatus.NEW);
        var mapped = response(1L, "A");
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(entity)));
        when(mapper.toResponse(entity)).thenReturn(mapped);

        Page<PlaylistResponse> result = service.findAll(Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(mapped);
    }

    @Test
    void update_appliesMappingAndReturnsResponse() {
        var request = new UpdatePlaylistRequest("New", "new desc", null);
        var existing = new Playlist(1L, "Old", "old desc", PlaylistStatus.NEW);
        var saved = new Playlist(1L, "New", "new desc", PlaylistStatus.OPEN);
        var expected = response(1L, "New");

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(expected);

        assertThat(service.update(1L, request)).isEqualTo(expected);
        verify(mapper).updateEntity(request, existing);
    }

    @Test
    void update_appliesMappingAndReturnsResponse_viaBuilder() {
        var existing = new Playlist(1L, "Old", "old desc", PlaylistStatus.NEW);
        var saved = new Playlist(1L, "New", "new desc", PlaylistStatus.OPEN);
        var expected = response(1L, "New");

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(expected);

        assertThat(service.update(1L, b -> b.name("New").description("new desc"))).isEqualTo(expected);
        verify(mapper).updateEntity(any(UpdatePlaylistRequest.class), eq(existing));
    }

    @Test
    void update_throwsNotFound_whenMissing() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, new UpdatePlaylistRequest("X", null, null)))
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
