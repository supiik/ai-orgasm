package com.orgasm.backend.playlist;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PlaylistMapper {

    PlaylistResponse toResponse(Playlist playlist);

    @Mapping(target = "status", defaultValue = "NEW")
    Playlist toEntity(CreatePlaylistRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(UpdatePlaylistRequest request, @MappingTarget Playlist playlist);
}
