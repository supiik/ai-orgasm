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

    @Mock
    PlaylistRepository repository;

    @InjectMocks
    PlaylistService service;

    @Test
    void create_savesAndReturnsPlaylist() {
        Playlist input = new Playlist(null, "My Mix", "desc");
        Playlist saved = new Playlist(1L, "My Mix", "desc");
        when(repository.save(input)).thenReturn(saved);

        assertThat(service.create(input)).isEqualTo(saved);
    }

    @Test
    void findById_returnsPlaylist_whenExists() {
        Playlist playlist = new Playlist(1L, "My Mix", "desc");
        when(repository.findById(1L)).thenReturn(Optional.of(playlist));

        assertThat(service.findById(1L)).contains(playlist);
    }

    @Test
    void findById_returnsEmpty_whenNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThat(service.findById(99L)).isEmpty();
    }

    @Test
    void findAll_returnsPage() {
        Page<Playlist> page = new PageImpl<>(List.of(new Playlist(1L, "A", null)));
        when(repository.findAll(any(Pageable.class))).thenReturn(page);

        assertThat(service.findAll(Pageable.unpaged())).isEqualTo(page);
    }

    @Test
    void update_updatesFieldsAndReturnsPlaylist() {
        Playlist existing = new Playlist(1L, "Old", "old desc");
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        Playlist result = service.update(1L, new Playlist(null, "New", "new desc"));

        assertThat(result.getName()).isEqualTo("New");
        assertThat(result.getDescription()).isEqualTo("new desc");
    }

    @Test
    void update_throwsNotFound_whenMissing() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, new Playlist()))
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
