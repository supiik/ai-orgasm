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

    static PlaylistResponse response(String id, String name) {
        return new PlaylistResponse(id, name, null, PlaylistStatus.NEW, 0L, Instant.EPOCH, Instant.EPOCH);
    }

    @Test
    void create_savesAndReturnsResponse() {
        var request = new CreatePlaylistRequest("My Mix", "desc", null);
        var entity = new Playlist(null, "My Mix", "desc", null);
        var saved = new Playlist("play-0001", "My Mix", "desc", PlaylistStatus.NEW);
        var expected = response("play-0001", "My Mix");

        when(mapper.toEntity(request)).thenReturn(entity);
        when(repository.save(entity)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(expected);

        assertThat(service.create(request)).isEqualTo(expected);
    }

    @Test
    void create_savesAndReturnsResponse_viaBuilder() {
        var entity = new Playlist(null, "My Mix", "desc", null);
        var saved = new Playlist("play-0001", "My Mix", "desc", PlaylistStatus.NEW);
        var expected = response("play-0001", "My Mix");

        when(mapper.toEntity(any(CreatePlaylistRequest.class))).thenReturn(entity);
        when(repository.save(entity)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(expected);

        assertThat(service.create(b -> b.name("My Mix").description("desc"))).isEqualTo(expected);
    }

    @Test
    void findById_returnsResponse_whenExists() {
        var entity = new Playlist("play-0001", "My Mix", "desc", PlaylistStatus.NEW);
        var expected = response("play-0001", "My Mix");
        when(repository.findById("play-0001")).thenReturn(Optional.of(entity));
        when(mapper.toResponse(entity)).thenReturn(expected);

        assertThat(service.findById("play-0001")).contains(expected);
    }

    @Test
    void findById_returnsEmpty_whenNotFound() {
        when(repository.findById("play-9999")).thenReturn(Optional.empty());

        assertThat(service.findById("play-9999")).isEmpty();
    }

    @Test
    void findAll_returnsMappedPage_whenNameIsNull() {
        var entity = new Playlist("play-0001", "A", null, PlaylistStatus.NEW);
        var mapped = response("play-0001", "A");
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(entity)));
        when(mapper.toResponse(entity)).thenReturn(mapped);

        Page<PlaylistResponse> result = service.findAll(FindPlaylistsRequest.builder().build(), Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(mapped);
    }

    @Test
    void findAll_returnsMappedPage_whenNameIsBlank() {
        var entity = new Playlist("play-0001", "A", null, PlaylistStatus.NEW);
        var mapped = response("play-0001", "A");
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(entity)));
        when(mapper.toResponse(entity)).thenReturn(mapped);

        Page<PlaylistResponse> result = service.findAll(new FindPlaylistsRequest("  "), Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(mapped);
    }

    @Test
    void findAll_filtersBy_name() {
        var entity = new Playlist("play-0001", "Chill", null, PlaylistStatus.NEW);
        var mapped = response("play-0001", "Chill");
        when(repository.findByNameContainingIgnoreCase(eq("chi"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));
        when(mapper.toResponse(entity)).thenReturn(mapped);

        Page<PlaylistResponse> result = service.findAll(new FindPlaylistsRequest("chi"), Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(mapped);
    }

    @Test
    void findAll_filtersBy_name_viaBuilder() {
        var entity = new Playlist("play-0001", "Chill", null, PlaylistStatus.NEW);
        var mapped = response("play-0001", "Chill");
        when(repository.findByNameContainingIgnoreCase(eq("chi"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));
        when(mapper.toResponse(entity)).thenReturn(mapped);

        Page<PlaylistResponse> result = service.findAll(b -> b.name("chi"), Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(mapped);
    }

    @Test
    void update_appliesMappingAndReturnsResponse() {
        var request = new UpdatePlaylistRequest("New", "new desc", null);
        var existing = new Playlist("play-0001", "Old", "old desc", PlaylistStatus.NEW);
        var saved = new Playlist("play-0001", "New", "new desc", PlaylistStatus.OPEN);
        var expected = response("play-0001", "New");

        when(repository.findById("play-0001")).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(expected);

        assertThat(service.update("play-0001", request)).isEqualTo(expected);
        verify(mapper).updateEntity(request, existing);
    }

    @Test
    void update_appliesMappingAndReturnsResponse_viaBuilder() {
        var existing = new Playlist("play-0001", "Old", "old desc", PlaylistStatus.NEW);
        var saved = new Playlist("play-0001", "New", "new desc", PlaylistStatus.OPEN);
        var expected = response("play-0001", "New");

        when(repository.findById("play-0001")).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(expected);

        assertThat(service.update("play-0001", b -> b.name("New").description("new desc"))).isEqualTo(expected);
        verify(mapper).updateEntity(any(UpdatePlaylistRequest.class), eq(existing));
    }

    @Test
    void update_throwsNotFound_whenMissing() {
        when(repository.findById("play-9999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("play-9999", new UpdatePlaylistRequest("X", null, null)))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("play-9999");
    }

    @Test
    void delete_softDeletes_whenExists() {
        when(repository.softDeleteById(eq("play-0001"), any())).thenReturn(1);

        service.delete("play-0001");

        verify(repository).softDeleteById(eq("play-0001"), any());
    }

    @Test
    void delete_throwsNotFound_whenMissing() {
        when(repository.softDeleteById(eq("play-9999"), any())).thenReturn(0);

        assertThatThrownBy(() -> service.delete("play-9999"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("play-9999");
    }
}
