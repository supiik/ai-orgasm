package com.orgasm.dynamo.playlist;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaylistServiceTest {

    @Mock PlaylistDynamoRepository repository;
    @Mock PlaylistMapper mapper;
    @InjectMocks PlaylistService service;

    private static PlaylistItem item(long id, String name) {
        PlaylistItem item = new PlaylistItem();
        item.setId(id);
        item.setTenantId(1L);
        item.setName(name);
        item.setStatus(PlaylistStatus.NEW.name());
        return item;
    }

    private static PlaylistResponse response(String id, String name) {
        return PlaylistResponse.builder().id(id).name(name).status(PlaylistStatus.NEW).build();
    }

    @Test
    void create_savesAndReturnsResponse() {
        var request = new CreatePlaylistRequest("My Mix", "desc", null, null);
        var expected = response("play-1", "My Mix");
        when(mapper.toItem(any(CreatePlaylistRequest.class), org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(inv -> item(inv.getArgument(2), "My Mix"));
        when(repository.save(any(PlaylistItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(PlaylistItem.class))).thenReturn(expected);

        assertThat(service.create(request)).isEqualTo(expected);
    }

    @Test
    void create_savesAndReturnsResponse_viaBuilder() {
        var expected = response("play-1", "My Mix");
        when(mapper.toItem(any(CreatePlaylistRequest.class), org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(inv -> item(inv.getArgument(2), "My Mix"));
        when(repository.save(any(PlaylistItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(PlaylistItem.class))).thenReturn(expected);

        assertThat(service.create(b -> b.name("My Mix").description("desc"))).isEqualTo(expected);
    }

    @Test
    void findById_returnsResponse_whenExists() {
        var stored = item(1L, "My Mix");
        var expected = response("play-1", "My Mix");
        when(repository.findById(1L, 1L)).thenReturn(Optional.of(stored));
        when(mapper.toResponse(stored)).thenReturn(expected);

        assertThat(service.findById(com.orgasm.dynamo.domain.IdGenerator.format("play", 1L))).contains(expected);
    }

    @Test
    void findById_returnsEmpty_whenNotFound() {
        when(repository.findById(1L, 1L)).thenReturn(Optional.empty());

        assertThat(service.findById(com.orgasm.dynamo.domain.IdGenerator.format("play", 1L))).isEmpty();
    }

    @Test
    void findById_returnsEmpty_whenSoftDeleted() {
        var stored = item(1L, "My Mix");
        stored.setDeletedAt(Instant.now());
        when(repository.findById(1L, 1L)).thenReturn(Optional.of(stored));

        assertThat(service.findById(com.orgasm.dynamo.domain.IdGenerator.format("play", 1L))).isEmpty();
    }

    @Test
    void findAll_filtersOutSoftDeleted_andPaginates() {
        var kept = item(1L, "Chill");
        var deleted = item(2L, "Gone");
        deleted.setDeletedAt(Instant.now());
        when(repository.findAllByTenant(1L)).thenReturn(List.of(kept, deleted));
        when(mapper.toResponse(kept)).thenReturn(response("play-1", "Chill"));

        Page<PlaylistResponse> result = service.findAll(FindPlaylistsRequest.builder().build(), Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(response("play-1", "Chill"));
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void findAll_filtersByName() {
        var chill = item(1L, "Chill Vibes");
        var rock = item(2L, "Rock Anthems");
        when(repository.findAllByTenant(1L)).thenReturn(List.of(chill, rock));
        when(mapper.toResponse(chill)).thenReturn(response("play-1", "Chill Vibes"));

        Page<PlaylistResponse> result = service.findAll(new FindPlaylistsRequest("chill"), Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(response("play-1", "Chill Vibes"));
    }

    @Test
    void findAll_filtersByName_viaBuilder() {
        var chill = item(1L, "Chill Vibes");
        when(repository.findAllByTenant(1L)).thenReturn(List.of(chill));
        when(mapper.toResponse(chill)).thenReturn(response("play-1", "Chill Vibes"));

        Page<PlaylistResponse> result = service.findAll(b -> b.name("chill"), Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(response("play-1", "Chill Vibes"));
    }

    @Test
    void findAll_respectsPageSize() {
        var first = item(1L, "A");
        var second = item(2L, "B");
        when(repository.findAllByTenant(1L)).thenReturn(List.of(first, second));
        when(mapper.toResponse(first)).thenReturn(response("play-1", "A"));

        Page<PlaylistResponse> result = service.findAll(FindPlaylistsRequest.builder().build(), PageRequest.of(0, 1));

        assertThat(result.getContent()).containsExactly(response("play-1", "A"));
        assertThat(result.getTotalElements()).isEqualTo(2);
    }
}
