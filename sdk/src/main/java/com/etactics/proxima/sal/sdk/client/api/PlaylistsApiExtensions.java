package com.etactics.proxima.sal.sdk.client.api;

import com.etactics.proxima.sal.sdk.client.ApiClient;
import com.etactics.proxima.sal.sdk.client.ApiException;
import com.etactics.proxima.sal.sdk.model.CreatePlaylistRequest;
import com.etactics.proxima.sal.sdk.model.PlaylistResponse;
import com.etactics.proxima.sal.sdk.model.UpdatePlaylistRequest;

import java.util.function.UnaryOperator;

public class PlaylistsApiExtensions extends PlaylistsApi {

    public PlaylistsApiExtensions() {
        super();
    }

    public PlaylistsApiExtensions(ApiClient apiClient) {
        super(apiClient);
    }

    public PlaylistResponse createPlaylist(UnaryOperator<CreatePlaylistRequest.CreatePlaylistRequestBuilder> customizer) throws ApiException {
        return createPlaylist(customizer.apply(CreatePlaylistRequest.builder()).build());
    }

    public PlaylistResponse updatePlaylist(String id, UnaryOperator<UpdatePlaylistRequest.UpdatePlaylistRequestBuilder> customizer) throws ApiException {
        return updatePlaylist(id, customizer.apply(UpdatePlaylistRequest.builder()).build());
    }
}
