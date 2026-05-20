package com.etactics.proxima.sal.sdk.facade;

import com.etactics.proxima.sal.sdk.client.api.PlaylistsApi;
import com.etactics.proxima.sal.sdk.model.CreatePlaylistRequest;
import com.etactics.proxima.sal.sdk.model.PlaylistPage;
import com.etactics.proxima.sal.sdk.model.PlaylistResponse;
import com.etactics.proxima.sal.sdk.model.UpdatePlaylistRequest;

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
