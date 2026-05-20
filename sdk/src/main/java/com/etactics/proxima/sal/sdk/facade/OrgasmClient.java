package com.etactics.proxima.sal.sdk.facade;

import com.etactics.proxima.sal.sdk.client.ApiClient;
import com.etactics.proxima.sal.sdk.client.api.ContributorsApi;
import com.etactics.proxima.sal.sdk.client.api.PlaylistsApi;
import com.etactics.proxima.sal.sdk.client.api.SongsApi;

public class OrgasmClient {

    private final PlaylistOperations playlists;
    private final SongOperations songs;
    private final ContributorOperations contributors;

    public OrgasmClient(String baseUrl) {
        ApiClient http = new ApiClient();
        http.updateBaseUri(baseUrl);
        this.playlists    = new PlaylistOperations(new PlaylistsApi(http));
        this.songs        = new SongOperations(new SongsApi(http));
        this.contributors = new ContributorOperations(new ContributorsApi(http));
    }

    OrgasmClient(PlaylistOperations playlists, SongOperations songs, ContributorOperations contributors) {
        this.playlists    = playlists;
        this.songs        = songs;
        this.contributors = contributors;
    }

    public PlaylistOperations    playlists()    { return playlists; }
    public SongOperations        songs()        { return songs; }
    public ContributorOperations contributors() { return contributors; }
}
