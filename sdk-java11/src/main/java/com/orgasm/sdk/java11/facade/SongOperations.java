package com.orgasm.sdk.java11.facade;

import com.orgasm.sdk.java11.api.SongsApi;
import com.orgasm.sdk.model.CreateSongRequest;
import com.orgasm.sdk.model.SongPage;
import com.orgasm.sdk.model.SongResponse;
import com.orgasm.sdk.model.UpdateSongRequest;

public class SongOperations {

    private final SongsApi api;

    SongOperations(SongsApi api) { this.api = api; }

    public SongPage     list(int page, int size)                              { return api.findAllSongs(page, size, "id", null); }
    public SongPage     list(int page, int size, String sort, String name)    { return api.findAllSongs(page, size, sort, name); }
    public SongResponse get(long id)                                          { return api.findSongById(id); }
    public SongResponse create(CreateSongRequest request)                     { return api.createSong(request); }
    public SongResponse update(long id, UpdateSongRequest request)            { return api.updateSong(id, request); }
    public void         delete(long id)                                       { api.deleteSong(id); }
}
