package com.orgasm.backend.nomination;

import com.orgasm.backend.domain.IdGenerator;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
        imports = {IdGenerator.class})
public interface NominationMapper {

    @Mapping(target = "id",            expression = "java(IdGenerator.format(\"nom\",  nomination.getId()))")
    @Mapping(target = "playlistId",    expression = "java(IdGenerator.format(\"play\", nomination.getPlaylistId()))")
    @Mapping(target = "songId",        expression = "java(IdGenerator.format(\"song\", nomination.getSongId()))")
    @Mapping(target = "nominatedById", expression = "java(IdGenerator.format(\"cont\", nomination.getNominatedById()))")
    NominationResponse toResponse(Nomination nomination);
}
