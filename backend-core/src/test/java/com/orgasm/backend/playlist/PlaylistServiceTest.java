package com.orgasm.backend.playlist;

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
class PlaylistServiceTest {

    @Mock PlaylistRepository repository;
    @Mock PlaylistMapper mapper;
    @InjectMocks PlaylistService service;

    static final long DB_ID = 1L;
    static final String USER_ID = IdGenerator.format("play", DB_ID);

    static PlaylistResponse response(String id, String name) {
        return new PlaylistResponse(id, name, null, PlaylistStatus.NEW, null, null, null, null, 0L, Instant.EPOCH, Instant.EPOCH);
    }

    @Test
    void create_savesAndReturnsResponse() {
        var request = new CreatePlaylistRequest("My Mix", "desc", null);
        var entity = new Playlist(null, null, "My Mix", "desc", null, null, null);
        var saved = new Playlist(DB_ID, null, "My Mix", "desc", PlaylistStatus.NEW, null, null);
        var expected = response(USER_ID, "My Mix");

        when(mapper.toEntity(request)).thenReturn(entity);
        when(repository.save(entity)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(expected);

        assertThat(service.create(request)).isEqualTo(expected);
    }

    @Test
    void create_savesAndReturnsResponse_viaBuilder() {
        var entity = new Playlist(null, null, "My Mix", "desc", null, null, null);
        var saved = new Playlist(DB_ID, null, "My Mix", "desc", PlaylistStatus.NEW, null, null);
        var expected = response(USER_ID, "My Mix");

        when(mapper.toEntity(any(CreatePlaylistRequest.class))).thenReturn(entity);
        when(repository.save(entity)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(expected);

        assertThat(service.create(b -> b.name("My Mix").description("desc"))).isEqualTo(expected);
    }

    @Test
    void findById_returnsResponse_whenExists() {
        var entity = new Playlist(DB_ID, null, "My Mix", "desc", PlaylistStatus.NEW, null, null);
        var expected = response(USER_ID, "My Mix");
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
        var entity = new Playlist(DB_ID, null, "A", null, PlaylistStatus.NEW, null, null);
        var mapped = response(USER_ID, "A");
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(entity)));
        when(mapper.toResponse(entity)).thenReturn(mapped);

        Page<PlaylistResponse> result = service.findAll(FindPlaylistsRequest.builder().build(), Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(mapped);
    }

    @Test
    void findAll_returnsMappedPage_whenNameIsBlank() {
        var entity = new Playlist(DB_ID, null, "A", null, PlaylistStatus.NEW, null, null);
        var mapped = response(USER_ID, "A");
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(entity)));
        when(mapper.toResponse(entity)).thenReturn(mapped);

        Page<PlaylistResponse> result = service.findAll(new FindPlaylistsRequest("  "), Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(mapped);
    }

    @Test
    void findAll_filtersBy_name() {
        var entity = new Playlist(DB_ID, null, "Chill", null, PlaylistStatus.NEW, null, null);
        var mapped = response(USER_ID, "Chill");
        when(repository.findByNameContainingIgnoreCase(eq("chi"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));
        when(mapper.toResponse(entity)).thenReturn(mapped);

        Page<PlaylistResponse> result = service.findAll(new FindPlaylistsRequest("chi"), Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(mapped);
    }

    @Test
    void findAll_filtersBy_name_viaBuilder() {
        var entity = new Playlist(DB_ID, null, "Chill", null, PlaylistStatus.NEW, null, null);
        var mapped = response(USER_ID, "Chill");
        when(repository.findByNameContainingIgnoreCase(eq("chi"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));
        when(mapper.toResponse(entity)).thenReturn(mapped);

        Page<PlaylistResponse> result = service.findAll(b -> b.name("chi"), Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(mapped);
    }

    @Test
    void update_appliesMappingAndReturnsResponse() {
        var request = new UpdatePlaylistRequest("New", "new desc", null);
        var existing = new Playlist(DB_ID, null, "Old", "old desc", PlaylistStatus.NEW, null, null);
        var saved = new Playlist(DB_ID, null, "New", "new desc", PlaylistStatus.OPEN, null, null);
        var expected = response(USER_ID, "New");

        when(repository.findById(DB_ID)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(expected);

        assertThat(service.update(USER_ID, request)).isEqualTo(expected);
        verify(mapper).updateEntity(request, existing);
    }

    @Test
    void update_appliesMappingAndReturnsResponse_viaBuilder() {
        var existing = new Playlist(DB_ID, null, "Old", "old desc", PlaylistStatus.NEW, null, null);
        var saved = new Playlist(DB_ID, null, "New", "new desc", PlaylistStatus.OPEN, null, null);
        var expected = response(USER_ID, "New");

        when(repository.findById(DB_ID)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(expected);

        assertThat(service.update(USER_ID, b -> b.name("New").description("new desc"))).isEqualTo(expected);
        verify(mapper).updateEntity(any(UpdatePlaylistRequest.class), eq(existing));
    }

    @Test
    void update_throwsNotFound_whenMissing() {
        when(repository.findById(DB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(USER_ID, new UpdatePlaylistRequest("X", null, null)))
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
