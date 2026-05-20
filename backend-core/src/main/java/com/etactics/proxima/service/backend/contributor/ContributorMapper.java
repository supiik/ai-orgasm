package com.etactics.proxima.service.backend.contributor;

import com.etactics.proxima.service.backend.domain.IdGenerator;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
        imports = {IdGenerator.class})
public interface ContributorMapper {

    @Mapping(target = "id", expression = "java(IdGenerator.format(\"cont\", contributor.getId()))")
    ContributorResponse toResponse(Contributor contributor);

    Contributor toEntity(CreateContributorRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(UpdateContributorRequest request, @MappingTarget Contributor contributor);
}
