package com.orgasm.dynamo.song;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SongServiceTest {

    @Mock SongDynamoRepository repository;
    @Mock SongMapper mapper;
    @InjectMocks SongService service;

    private static SongItem item(long id, String name) {
        SongItem item = new SongItem();
        item.setId(id);
        item.setTenantId(1L);
        item.setArtist("Some Artist");
        item.setName(name);
        return item;
    }

    private static SongResponse response(String id, String name) {
        return SongResponse.builder().id(id).artist("Some Artist").name(name).build();
    }

    @Test
    void create_savesAndReturnsResponse() {
        var request = new CreateSongRequest("Some Artist", "My Song", "My Album", 2020, "http://example.com/song");
        var expected = response("song-1", "My Song");
        when(mapper.toItem(any(CreateSongRequest.class), org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(inv -> item(inv.getArgument(2), "My Song"));
        when(repository.save(any(SongItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(SongItem.class))).thenReturn(expected);

        assertThat(service.create(request)).isEqualTo(expected);
    }

    @Test
    void create_savesAndReturnsResponse_viaBuilder() {
        var expected = response("song-1", "My Song");
        when(mapper.toItem(any(CreateSongRequest.class), org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(inv -> item(inv.getArgument(2), "My Song"));
        when(repository.save(any(SongItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(SongItem.class))).thenReturn(expected);

        assertThat(service.create(b -> b.artist("Some Artist").name("My Song"))).isEqualTo(expected);
    }

    @Test
    void findById_returnsResponse_whenExists() {
        var stored = item(1L, "My Song");
        var expected = response("song-1", "My Song");
        when(repository.findById(1L, 1L)).thenReturn(Optional.of(stored));
        when(mapper.toResponse(stored)).thenReturn(expected);

        assertThat(service.findById(com.orgasm.dynamo.domain.IdGenerator.format("song", 1L))).contains(expected);
    }

    @Test
    void findById_returnsEmpty_whenNotFound() {
        when(repository.findById(1L, 1L)).thenReturn(Optional.empty());

        assertThat(service.findById(com.orgasm.dynamo.domain.IdGenerator.format("song", 1L))).isEmpty();
    }

    @Test
    void findById_returnsEmpty_whenSoftDeleted() {
        var stored = item(1L, "My Song");
        stored.setDeletedAt(Instant.now());
        when(repository.findById(1L, 1L)).thenReturn(Optional.of(stored));

        assertThat(service.findById(com.orgasm.dynamo.domain.IdGenerator.format("song", 1L))).isEmpty();
    }

    @Test
    void findAll_filtersOutSoftDeleted_andPaginates() {
        var kept = item(1L, "Chill Song");
        var deleted = item(2L, "Gone Song");
        deleted.setDeletedAt(Instant.now());
        when(repository.findAllByTenant(1L)).thenReturn(List.of(kept, deleted));
        when(mapper.toResponse(kept)).thenReturn(response("song-1", "Chill Song"));

        Page<SongResponse> result = service.findAll(FindSongsRequest.builder().build(), Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(response("song-1", "Chill Song"));
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void findAll_filtersByName() {
        var chill = item(1L, "Chill Vibes");
        var rock = item(2L, "Rock Anthems");
        when(repository.findAllByTenant(1L)).thenReturn(List.of(chill, rock));
        when(mapper.toResponse(chill)).thenReturn(response("song-1", "Chill Vibes"));

        Page<SongResponse> result = service.findAll(new FindSongsRequest("chill"), Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(response("song-1", "Chill Vibes"));
    }

    @Test
    void findAll_filtersByName_viaBuilder() {
        var chill = item(1L, "Chill Vibes");
        when(repository.findAllByTenant(1L)).thenReturn(List.of(chill));
        when(mapper.toResponse(chill)).thenReturn(response("song-1", "Chill Vibes"));

        Page<SongResponse> result = service.findAll(b -> b.name("chill"), Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(response("song-1", "Chill Vibes"));
    }

    @Test
    void findAll_respectsPageSize() {
        var first = item(1L, "A");
        var second = item(2L, "B");
        when(repository.findAllByTenant(1L)).thenReturn(List.of(first, second));
        when(mapper.toResponse(first)).thenReturn(response("song-1", "A"));

        Page<SongResponse> result = service.findAll(FindSongsRequest.builder().build(), PageRequest.of(0, 1));

        assertThat(result.getContent()).containsExactly(response("song-1", "A"));
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    private static String externalId(long id) {
        return com.orgasm.dynamo.domain.IdGenerator.format("song", id);
    }

    @Test
    void update_appliesMappingAndReturnsResponse() {
        var existing = item(1L, "Old");
        var expected = response(externalId(1L), "New");
        when(repository.findById(1L, 1L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);
        when(mapper.toResponse(existing)).thenReturn(expected);

        assertThat(service.update(externalId(1L), new UpdateSongRequest("Some Artist", "New", "new album", 2021, null)))
                .isEqualTo(expected);
        verify(mapper).updateItem(any(UpdateSongRequest.class), org.mockito.ArgumentMatchers.eq(existing));
    }

    @Test
    void update_appliesMappingAndReturnsResponse_viaBuilder() {
        var existing = item(1L, "Old");
        var expected = response(externalId(1L), "New");
        when(repository.findById(1L, 1L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);
        when(mapper.toResponse(existing)).thenReturn(expected);

        assertThat(service.update(externalId(1L), b -> b.artist("Some Artist").name("New").album("new album")))
                .isEqualTo(expected);
    }

    @Test
    void update_throwsNotFound_whenMissing() {
        when(repository.findById(1L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(externalId(1L), new UpdateSongRequest("Artist", "X", null, null, null)))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessageContaining(externalId(1L));
    }

    @Test
    void update_throwsNotFound_whenSoftDeleted() {
        var deleted = item(1L, "Old");
        deleted.setDeletedAt(Instant.now());
        when(repository.findById(1L, 1L)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.update(externalId(1L), new UpdateSongRequest("Artist", "X", null, null, null)))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void delete_softDeletes_whenExists() {
        var existing = item(1L, "Old");
        when(repository.findById(1L, 1L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        service.delete(externalId(1L));

        assertThat(existing.getDeletedAt()).isNotNull();
        verify(repository).save(existing);
    }

    @Test
    void delete_throwsNotFound_whenMissing() {
        when(repository.findById(1L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(externalId(1L)))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessageContaining(externalId(1L));
    }
}
