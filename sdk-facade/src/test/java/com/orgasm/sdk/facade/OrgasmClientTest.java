package com.orgasm.sdk.facade;

import com.orgasm.sdk.client.api.ContributorsApi;
import com.orgasm.sdk.client.api.PlaylistsApi;
import com.orgasm.sdk.client.api.SongsApi;
import com.orgasm.sdk.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrgasmClientTest {

    @Mock PlaylistsApi    playlistsApi;
    @Mock SongsApi        songsApi;
    @Mock ContributorsApi contributorsApi;

    OrgasmClient client;

    @BeforeEach
    void setUp() {
        client = new OrgasmClient(
                new PlaylistOperations(playlistsApi),
                new SongOperations(songsApi),
                new ContributorOperations(contributorsApi));
    }

    // ── Playlists ─────────────────────────────────────────────────────────────

    @Test
    void playlists_list_defaultSort() {
        var page = new PlaylistPage();
        when(playlistsApi.findAllPlaylists(0, 20, "id", null)).thenReturn(page);
        assertThat(client.playlists().list(0, 20)).isSameAs(page);
    }

    @Test
    void playlists_list_withNameFilter() {
        var page = new PlaylistPage();
        when(playlistsApi.findAllPlaylists(0, 10, "name", "chill")).thenReturn(page);
        assertThat(client.playlists().list(0, 10, "name", "chill")).isSameAs(page);
    }

    @Test
    void playlists_get() {
        var response = new PlaylistResponse();
        when(playlistsApi.findPlaylistById(1L)).thenReturn(response);
        assertThat(client.playlists().get(1L)).isSameAs(response);
    }

    @Test
    void playlists_create() {
        var request = CreatePlaylistRequest.builder().name("My Mix").build();
        var response = new PlaylistResponse();
        when(playlistsApi.createPlaylist(request)).thenReturn(response);
        assertThat(client.playlists().create(request)).isSameAs(response);
    }

    @Test
    void playlists_update() {
        var request = UpdatePlaylistRequest.builder().name("Updated").build();
        var response = new PlaylistResponse();
        when(playlistsApi.updatePlaylist(2L, request)).thenReturn(response);
        assertThat(client.playlists().update(2L, request)).isSameAs(response);
    }

    @Test
    void playlists_delete() {
        client.playlists().delete(3L);
        verify(playlistsApi).deletePlaylist(3L);
    }

    // ── Songs ─────────────────────────────────────────────────────────────────

    @Test
    void songs_list_defaultSort() {
        var page = new SongPage();
        when(songsApi.findAllSongs(0, 20, "id", null)).thenReturn(page);
        assertThat(client.songs().list(0, 20)).isSameAs(page);
    }

    @Test
    void songs_list_withNameFilter() {
        var page = new SongPage();
        when(songsApi.findAllSongs(0, 10, "name", "creep")).thenReturn(page);
        assertThat(client.songs().list(0, 10, "name", "creep")).isSameAs(page);
    }

    @Test
    void songs_get() {
        var response = new SongResponse();
        when(songsApi.findSongById(1L)).thenReturn(response);
        assertThat(client.songs().get(1L)).isSameAs(response);
    }

    @Test
    void songs_create() {
        var request = CreateSongRequest.builder().artist("Radiohead").name("Creep").build();
        var response = new SongResponse();
        when(songsApi.createSong(request)).thenReturn(response);
        assertThat(client.songs().create(request)).isSameAs(response);
    }

    @Test
    void songs_update() {
        var request = UpdateSongRequest.builder().artist("Radiohead").name("Karma Police").build();
        var response = new SongResponse();
        when(songsApi.updateSong(2L, request)).thenReturn(response);
        assertThat(client.songs().update(2L, request)).isSameAs(response);
    }

    @Test
    void songs_delete() {
        client.songs().delete(3L);
        verify(songsApi).deleteSong(3L);
    }

    // ── Contributors ──────────────────────────────────────────────────────────

    @Test
    void contributors_list_defaultSort() {
        var page = new ContributorPage();
        when(contributorsApi.findAllContributors(0, 20, "id", null)).thenReturn(page);
        assertThat(client.contributors().list(0, 20)).isSameAs(page);
    }

    @Test
    void contributors_list_withNameFilter() {
        var page = new ContributorPage();
        when(contributorsApi.findAllContributors(0, 10, "name", "thom")).thenReturn(page);
        assertThat(client.contributors().list(0, 10, "name", "thom")).isSameAs(page);
    }

    @Test
    void contributors_get() {
        var response = new ContributorResponse();
        when(contributorsApi.findContributorById(1L)).thenReturn(response);
        assertThat(client.contributors().get(1L)).isSameAs(response);
    }

    @Test
    void contributors_create() {
        var request = CreateContributorRequest.builder().name("Thom Yorke").build();
        var response = new ContributorResponse();
        when(contributorsApi.createContributor(request)).thenReturn(response);
        assertThat(client.contributors().create(request)).isSameAs(response);
    }

    @Test
    void contributors_update() {
        var request = UpdateContributorRequest.builder().name("Jonny Greenwood").build();
        var response = new ContributorResponse();
        when(contributorsApi.updateContributor(2L, request)).thenReturn(response);
        assertThat(client.contributors().update(2L, request)).isSameAs(response);
    }

    @Test
    void contributors_delete() {
        client.contributors().delete(3L);
        verify(contributorsApi).deleteContributor(3L);
    }
}
