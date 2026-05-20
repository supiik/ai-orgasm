package com.etactics.proxima.sal.sdk.java11.facade;

import com.etactics.proxima.sal.sdk.java11.api.SongsApi;
import com.etactics.proxima.sal.sdk.model.CreateSongRequest;
import com.etactics.proxima.sal.sdk.model.SongPage;
import com.etactics.proxima.sal.sdk.model.SongResponse;
import com.etactics.proxima.sal.sdk.model.UpdateSongRequest;

public class SongOperations {

    private final SongsApi api;

    SongOperations(SongsApi api) { this.api = api; }

    public SongPage     list(int page, int size)                              { return api.findAllSongs(page, size, "id", null); }
    public SongPage     list(int page, int size, String sort, String name)    { return api.findAllSongs(page, size, sort, name); }
    public SongResponse get(String id)                                        { return api.findSongById(id); }
    public SongResponse create(CreateSongRequest request)                     { return api.createSong(request); }
    public SongResponse update(String id, UpdateSongRequest request)          { return api.updateSong(id, request); }
    public void         delete(String id)                                     { api.deleteSong(id); }
}
