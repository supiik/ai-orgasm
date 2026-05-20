package com.etactics.proxima.sal.backend.song;

import com.etactics.proxima.sal.backend.domain.IdGenerator;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
        imports = {IdGenerator.class})
public interface SongMapper {

    @Mapping(target = "id", expression = "java(IdGenerator.format(\"song\", song.getId()))")
    SongResponse toResponse(Song song);

    Song toEntity(CreateSongRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(UpdateSongRequest request, @MappingTarget Song song);
}
