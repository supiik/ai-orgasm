package com.pi2.anchor.backend.sample;

import com.pi2.anchor.backend.domain.IdGenerator;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SampleMapperTest {

    private final SampleMapper mapper = Mappers.getMapper(SampleMapper.class);

    static Sample entity(long id) {
        return new Sample(id, null, "Widget", "A widget", "test@example.com", 5, 1000L, 4.5,
                new BigDecimal("9.99"), true, LocalDate.of(2000, 1, 1),
                LocalDateTime.of(2024, 6, 1, 10, 0), SampleStatus.ACTIVE, "some notes");
    }

    @Test
    void toResponse_returnsNull_whenInputNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }

    @Test
    void toResponse_copiesAllFields() {
        Sample sample = entity(7L);

        SampleResponse response = mapper.toResponse(sample);

        assertThat(response).isNotNull();
        assertThat(response.id()).startsWith("smpl-");
        assertThat(IdGenerator.parse(response.id())).isEqualTo(7L);
        assertThat(response.name()).isEqualTo("Widget");
        assertThat(response.description()).isEqualTo("A widget");
        assertThat(response.email()).isEqualTo("test@example.com");
        assertThat(response.quantity()).isEqualTo(5);
        assertThat(response.largeNumber()).isEqualTo(1000L);
        assertThat(response.rating()).isEqualTo(4.5);
        assertThat(response.price()).isEqualByComparingTo("9.99");
        assertThat(response.active()).isTrue();
        assertThat(response.birthDate()).isEqualTo(LocalDate.of(2000, 1, 1));
        assertThat(response.scheduledAt()).isEqualTo(LocalDateTime.of(2024, 6, 1, 10, 0));
        assertThat(response.status()).isEqualTo(SampleStatus.ACTIVE);
        assertThat(response.notes()).isEqualTo("some notes");
    }

    @Test
    void toEntity_returnsNull_whenInputNull() {
        assertThat(mapper.toEntity(null)).isNull();
    }

    @Test
    void toEntity_defaultsStatusToDraft_whenNotProvided() {
        CreateSampleRequest request = new CreateSampleRequest("Widget", null, null, 0, 0L, 0.0, null, false, null, null, null, null);

        Sample entity = mapper.toEntity(request);

        assertThat(entity).isNotNull();
        assertThat(entity.getName()).isEqualTo("Widget");
        assertThat(entity.getStatus()).isEqualTo(SampleStatus.DRAFT);
    }

    @Test
    void toEntity_usesProvidedStatus() {
        CreateSampleRequest request = new CreateSampleRequest("Widget", null, null, 0, 0L, 0.0, null, false, null, null, SampleStatus.ACTIVE, null);

        Sample entity = mapper.toEntity(request);

        assertThat(entity.getStatus()).isEqualTo(SampleStatus.ACTIVE);
    }

    @Test
    void updateEntity_isNoOp_whenRequestNull() {
        Sample sample = entity(1L);

        mapper.updateEntity(null, sample);

        assertThat(sample.getName()).isEqualTo("Widget");
        assertThat(sample.getStatus()).isEqualTo(SampleStatus.ACTIVE);
    }

    @Test
    void updateEntity_updatesAllFields() {
        Sample sample = entity(1L);
        UpdateSampleRequest request = new UpdateSampleRequest(
                "Gadget", "New desc", "new@example.com", 10, 2000L, 3.0,
                new BigDecimal("19.99"), false, LocalDate.of(1990, 5, 15),
                LocalDateTime.of(2025, 1, 1, 8, 0), SampleStatus.ARCHIVED, "updated notes");

        mapper.updateEntity(request, sample);

        assertThat(sample.getName()).isEqualTo("Gadget");
        assertThat(sample.getDescription()).isEqualTo("New desc");
        assertThat(sample.getEmail()).isEqualTo("new@example.com");
        assertThat(sample.getQuantity()).isEqualTo(10);
        assertThat(sample.getLargeNumber()).isEqualTo(2000L);
        assertThat(sample.getRating()).isEqualTo(3.0);
        assertThat(sample.getPrice()).isEqualByComparingTo("19.99");
        assertThat(sample.isActive()).isFalse();
        assertThat(sample.getBirthDate()).isEqualTo(LocalDate.of(1990, 5, 15));
        assertThat(sample.getScheduledAt()).isEqualTo(LocalDateTime.of(2025, 1, 1, 8, 0));
        assertThat(sample.getStatus()).isEqualTo(SampleStatus.ARCHIVED);
        assertThat(sample.getNotes()).isEqualTo("updated notes");
    }

    @Test
    void updateEntity_preservesFields_whenNullInRequest() {
        Sample sample = entity(1L);
        UpdateSampleRequest request = new UpdateSampleRequest("Renamed", null, null, null, null, null, null, null, null, null, null, null);

        mapper.updateEntity(request, sample);

        assertThat(sample.getName()).isEqualTo("Renamed");
        assertThat(sample.getStatus()).isEqualTo(SampleStatus.ACTIVE);
        assertThat(sample.getQuantity()).isEqualTo(5);
    }
}
