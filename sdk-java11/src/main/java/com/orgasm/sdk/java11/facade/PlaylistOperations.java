package com.orgasm.sdk.java11.facade;

import com.orgasm.sdk.java11.api.PlaylistsApi;
import com.orgasm.sdk.model.CreatePlaylistRequest;
import com.orgasm.sdk.model.PlaylistPage;
import com.orgasm.sdk.model.PlaylistResponse;
import com.orgasm.sdk.model.UpdatePlaylistRequest;

public class PlaylistOperations {

    private final PlaylistsApi api;

    PlaylistOperations(PlaylistsApi api) { this.api = api; }

    public PlaylistPage     list(int page, int size)                              { return api.findAllPlaylists(page, size, "id", null); }
    public PlaylistPage     list(int page, int size, String sort, String name)    { return api.findAllPlaylists(page, size, sort, name); }
    public PlaylistResponse get(long id)                                          { return api.findPlaylistById(id); }
    public PlaylistResponse create(CreatePlaylistRequest request)                 { return api.createPlaylist(request); }
    public PlaylistResponse update(long id, UpdatePlaylistRequest request)        { return api.updatePlaylist(id, request); }
    public void             delete(long id)                                       { api.deletePlaylist(id); }
}
