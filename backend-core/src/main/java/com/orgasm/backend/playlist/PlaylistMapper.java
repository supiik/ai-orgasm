package com.orgasm.backend.playlist;

import com.orgasm.backend.domain.IdGenerator;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
        imports = {IdGenerator.class})
public interface PlaylistMapper {

    @Mapping(target = "id", expression = "java(IdGenerator.format(\"play\", playlist.getId()))")
    @Mapping(target = "leadContributorId",      expression = "java(playlist.getLeadContributor() != null ? IdGenerator.format(\"cont\", playlist.getLeadContributor().getId()) : null)")
    @Mapping(target = "leadContributorName",    expression = "java(playlist.getLeadContributor() != null ? playlist.getLeadContributor().getName() : null)")
    @Mapping(target = "leadContributorAvatarUrl", expression = "java(playlist.getLeadContributor() != null ? playlist.getLeadContributor().getAvatarUrl() : null)")
    PlaylistResponse toResponse(Playlist playlist);

    @Mapping(target = "status", defaultValue = "NEW")
    Playlist toEntity(CreatePlaylistRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(UpdatePlaylistRequest request, @MappingTarget Playlist playlist);
}
