package com.orgasm.sdk.java8.api;

import com.orgasm.sdk.java8.ApiClient;
import com.orgasm.sdk.java8.ApiException;
import com.orgasm.sdk.java8.model.CreatePlaylistRequest;
import com.orgasm.sdk.java8.model.PlaylistResponse;
import com.orgasm.sdk.java8.model.UpdatePlaylistRequest;

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
