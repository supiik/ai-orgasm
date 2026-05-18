package com.orgasm.sdk.java8.facade;

import com.orgasm.sdk.java8.api.ContributorsApi;
import com.orgasm.sdk.java8.api.PlaylistsApi;
import com.orgasm.sdk.java8.api.SongsApi;
import com.orgasm.sdk.java8.model.*;
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

    @Test void playlists_list_defaultSort() {
        PlaylistPage page = new PlaylistPage();
        when(playlistsApi.findAllPlaylists(0, 20, "id", null)).thenReturn(page);
        assertThat(client.playlists().list(0, 20)).isSameAs(page);
    }

    @Test void playlists_list_withNameFilter() {
        PlaylistPage page = new PlaylistPage();
        when(playlistsApi.findAllPlaylists(0, 10, "name", "chill")).thenReturn(page);
        assertThat(client.playlists().list(0, 10, "name", "chill")).isSameAs(page);
    }

    @Test void playlists_get() {
        PlaylistResponse r = new PlaylistResponse();
        when(playlistsApi.findPlaylistById(1L)).thenReturn(r);
        assertThat(client.playlists().get(1L)).isSameAs(r);
    }

    @Test void playlists_create() {
        CreatePlaylistRequest req = new CreatePlaylistRequest().name("My Mix");
        PlaylistResponse r = new PlaylistResponse();
        when(playlistsApi.createPlaylist(req)).thenReturn(r);
        assertThat(client.playlists().create(req)).isSameAs(r);
    }

    @Test void playlists_update() {
        UpdatePlaylistRequest req = new UpdatePlaylistRequest().name("Updated");
        PlaylistResponse r = new PlaylistResponse();
        when(playlistsApi.updatePlaylist(2L, req)).thenReturn(r);
        assertThat(client.playlists().update(2L, req)).isSameAs(r);
    }

    @Test void playlists_delete() {
        client.playlists().delete(3L);
        verify(playlistsApi).deletePlaylist(3L);
    }

    @Test void songs_list_defaultSort() {
        SongPage page = new SongPage();
        when(songsApi.findAllSongs(0, 20, "id", null)).thenReturn(page);
        assertThat(client.songs().list(0, 20)).isSameAs(page);
    }

    @Test void songs_list_withNameFilter() {
        SongPage page = new SongPage();
        when(songsApi.findAllSongs(0, 10, "name", "creep")).thenReturn(page);
        assertThat(client.songs().list(0, 10, "name", "creep")).isSameAs(page);
    }

    @Test void songs_get() {
        SongResponse r = new SongResponse();
        when(songsApi.findSongById(1L)).thenReturn(r);
        assertThat(client.songs().get(1L)).isSameAs(r);
    }

    @Test void songs_create() {
        CreateSongRequest req = new CreateSongRequest().artist("Radiohead").name("Creep");
        SongResponse r = new SongResponse();
        when(songsApi.createSong(req)).thenReturn(r);
        assertThat(client.songs().create(req)).isSameAs(r);
    }

    @Test void songs_update() {
        UpdateSongRequest req = new UpdateSongRequest().artist("Radiohead").name("Karma Police");
        SongResponse r = new SongResponse();
        when(songsApi.updateSong(2L, req)).thenReturn(r);
        assertThat(client.songs().update(2L, req)).isSameAs(r);
    }

    @Test void songs_delete() {
        client.songs().delete(3L);
        verify(songsApi).deleteSong(3L);
    }

    @Test void contributors_list_defaultSort() {
        ContributorPage page = new ContributorPage();
        when(contributorsApi.findAllContributors(0, 20, "id", null)).thenReturn(page);
        assertThat(client.contributors().list(0, 20)).isSameAs(page);
    }

    @Test void contributors_list_withNameFilter() {
        ContributorPage page = new ContributorPage();
        when(contributorsApi.findAllContributors(0, 10, "name", "thom")).thenReturn(page);
        assertThat(client.contributors().list(0, 10, "name", "thom")).isSameAs(page);
    }

    @Test void contributors_get() {
        ContributorResponse r = new ContributorResponse();
        when(contributorsApi.findContributorById(1L)).thenReturn(r);
        assertThat(client.contributors().get(1L)).isSameAs(r);
    }

    @Test void contributors_create() {
        CreateContributorRequest req = new CreateContributorRequest().name("Thom Yorke");
        ContributorResponse r = new ContributorResponse();
        when(contributorsApi.createContributor(req)).thenReturn(r);
        assertThat(client.contributors().create(req)).isSameAs(r);
    }

    @Test void contributors_update() {
        UpdateContributorRequest req = new UpdateContributorRequest().name("Jonny Greenwood");
        ContributorResponse r = new ContributorResponse();
        when(contributorsApi.updateContributor(2L, req)).thenReturn(r);
        assertThat(client.contributors().update(2L, req)).isSameAs(r);
    }

    @Test void contributors_delete() {
        client.contributors().delete(3L);
        verify(contributorsApi).deleteContributor(3L);
    }
}
