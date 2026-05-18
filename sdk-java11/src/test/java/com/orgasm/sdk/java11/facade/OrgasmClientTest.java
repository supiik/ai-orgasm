package com.orgasm.sdk.java11.facade;

import com.orgasm.sdk.java11.api.ContributorsApi;
import com.orgasm.sdk.java11.api.PlaylistsApi;
import com.orgasm.sdk.java11.api.SongsApi;
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

    @Test void playlists_list_defaultSort() {
        var page = new PlaylistPage();
        when(playlistsApi.findAllPlaylists(0, 20, "id", null)).thenReturn(page);
        assertThat(client.playlists().list(0, 20)).isSameAs(page);
    }

    @Test void playlists_list_withNameFilter() {
        var page = new PlaylistPage();
        when(playlistsApi.findAllPlaylists(0, 10, "name", "chill")).thenReturn(page);
        assertThat(client.playlists().list(0, 10, "name", "chill")).isSameAs(page);
    }

    @Test void playlists_get() {
        var r = new PlaylistResponse();
        when(playlistsApi.findPlaylistById("play_0001")).thenReturn(r);
        assertThat(client.playlists().get("play_0001")).isSameAs(r);
    }

    @Test void playlists_create() {
        var req = CreatePlaylistRequest.builder().name("My Mix").build();
        var r = new PlaylistResponse();
        when(playlistsApi.createPlaylist(req)).thenReturn(r);
        assertThat(client.playlists().create(req)).isSameAs(r);
    }

    @Test void playlists_update() {
        var req = UpdatePlaylistRequest.builder().name("Updated").build();
        var r = new PlaylistResponse();
        when(playlistsApi.updatePlaylist("play_0002", req)).thenReturn(r);
        assertThat(client.playlists().update("play_0002", req)).isSameAs(r);
    }

    @Test void playlists_delete() {
        client.playlists().delete("play_0003");
        verify(playlistsApi).deletePlaylist("play_0003");
    }

    @Test void songs_list_defaultSort() {
        var page = new SongPage();
        when(songsApi.findAllSongs(0, 20, "id", null)).thenReturn(page);
        assertThat(client.songs().list(0, 20)).isSameAs(page);
    }

    @Test void songs_list_withNameFilter() {
        var page = new SongPage();
        when(songsApi.findAllSongs(0, 10, "name", "creep")).thenReturn(page);
        assertThat(client.songs().list(0, 10, "name", "creep")).isSameAs(page);
    }

    @Test void songs_get() {
        var r = new SongResponse();
        when(songsApi.findSongById("song_0001")).thenReturn(r);
        assertThat(client.songs().get("song_0001")).isSameAs(r);
    }

    @Test void songs_create() {
        var req = CreateSongRequest.builder().artist("Radiohead").name("Creep").build();
        var r = new SongResponse();
        when(songsApi.createSong(req)).thenReturn(r);
        assertThat(client.songs().create(req)).isSameAs(r);
    }

    @Test void songs_update() {
        var req = UpdateSongRequest.builder().artist("Radiohead").name("Karma Police").build();
        var r = new SongResponse();
        when(songsApi.updateSong("song_0002", req)).thenReturn(r);
        assertThat(client.songs().update("song_0002", req)).isSameAs(r);
    }

    @Test void songs_delete() {
        client.songs().delete("song_0003");
        verify(songsApi).deleteSong("song_0003");
    }

    @Test void contributors_list_defaultSort() {
        var page = new ContributorPage();
        when(contributorsApi.findAllContributors(0, 20, "id", null)).thenReturn(page);
        assertThat(client.contributors().list(0, 20)).isSameAs(page);
    }

    @Test void contributors_list_withNameFilter() {
        var page = new ContributorPage();
        when(contributorsApi.findAllContributors(0, 10, "name", "thom")).thenReturn(page);
        assertThat(client.contributors().list(0, 10, "name", "thom")).isSameAs(page);
    }

    @Test void contributors_get() {
        var r = new ContributorResponse();
        when(contributorsApi.findContributorById("cont_0001")).thenReturn(r);
        assertThat(client.contributors().get("cont_0001")).isSameAs(r);
    }

    @Test void contributors_create() {
        var req = CreateContributorRequest.builder().name("Thom Yorke").build();
        var r = new ContributorResponse();
        when(contributorsApi.createContributor(req)).thenReturn(r);
        assertThat(client.contributors().create(req)).isSameAs(r);
    }

    @Test void contributors_update() {
        var req = UpdateContributorRequest.builder().name("Jonny Greenwood").build();
        var r = new ContributorResponse();
        when(contributorsApi.updateContributor("cont_0002", req)).thenReturn(r);
        assertThat(client.contributors().update("cont_0002", req)).isSameAs(r);
    }

    @Test void contributors_delete() {
        client.contributors().delete("cont_0003");
        verify(contributorsApi).deleteContributor("cont_0003");
    }
}
