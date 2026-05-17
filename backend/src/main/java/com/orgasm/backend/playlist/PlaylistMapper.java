package com.orgasm.backend.playlist;

import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PlaylistMapper {

    PlaylistResponse toResponse(Playlist playlist);

    Playlist toEntity(CreatePlaylistRequest request);

    void updateEntity(UpdatePlaylistRequest request, @MappingTarget Playlist playlist);
}
