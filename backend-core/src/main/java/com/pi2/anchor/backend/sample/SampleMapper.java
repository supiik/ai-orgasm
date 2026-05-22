package com.pi2.anchor.backend.sample;

import com.pi2.anchor.backend.domain.IdGenerator;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
        imports = {IdGenerator.class})
public interface SampleMapper {

    @Mapping(target = "id", expression = "java(IdGenerator.format(\"smpl\", sample.getId()))")
    SampleResponse toResponse(Sample sample);

    @Mapping(target = "status", defaultValue = "DRAFT")
    Sample toEntity(CreateSampleRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(UpdateSampleRequest request, @MappingTarget Sample sample);
}
