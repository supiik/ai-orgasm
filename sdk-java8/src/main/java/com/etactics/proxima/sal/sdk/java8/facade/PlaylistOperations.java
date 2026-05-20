package com.etactics.proxima.sal.sdk.java8.facade;

import com.etactics.proxima.sal.sdk.java8.api.PlaylistsApi;
import com.etactics.proxima.sal.sdk.java8.model.CreatePlaylistRequest;
import com.etactics.proxima.sal.sdk.java8.model.PlaylistPage;
import com.etactics.proxima.sal.sdk.java8.model.PlaylistResponse;
import com.etactics.proxima.sal.sdk.java8.model.UpdatePlaylistRequest;

public class PlaylistOperations {

    private final PlaylistsApi api;

    PlaylistOperations(PlaylistsApi api) { this.api = api; }

    public PlaylistPage     list(int page, int size)                              { return api.findAllPlaylists(page, size, "id", null); }
    public PlaylistPage     list(int page, int size, String sort, String name)    { return api.findAllPlaylists(page, size, sort, name); }
    public PlaylistResponse get(String id)                                        { return api.findPlaylistById(id); }
    public PlaylistResponse create(CreatePlaylistRequest request)                 { return api.createPlaylist(request); }
    public PlaylistResponse update(String id, UpdatePlaylistRequest request)      { return api.updatePlaylist(id, request); }
    public void             delete(String id)                                     { api.deletePlaylist(id); }
}
