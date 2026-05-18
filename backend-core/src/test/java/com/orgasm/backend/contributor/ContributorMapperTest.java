package com.orgasm.backend.contributor;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class ContributorMapperTest {

    private final ContributorMapper mapper = Mappers.getMapper(ContributorMapper.class);

    @Test
    void toResponse_returnsNull_whenInputNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }

    @Test
    void toResponse_copiesAllFields() {
        Contributor contributor = new Contributor(7L, "Alice", "alice@example.com");

        ContributorResponse response = mapper.toResponse(contributor);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.name()).isEqualTo("Alice");
        assertThat(response.email()).isEqualTo("alice@example.com");
    }

    @Test
    void toEntity_returnsNull_whenInputNull() {
        assertThat(mapper.toEntity(null)).isNull();
    }

    @Test
    void toEntity_mapsAllFields() {
        CreateContributorRequest request = new CreateContributorRequest("Bob", "bob@example.com");

        Contributor entity = mapper.toEntity(request);

        assertThat(entity).isNotNull();
        assertThat(entity.getName()).isEqualTo("Bob");
        assertThat(entity.getEmail()).isEqualTo("bob@example.com");
    }

    @Test
    void toEntity_handlesNullEmail() {
        CreateContributorRequest request = new CreateContributorRequest("Bob", null);

        Contributor entity = mapper.toEntity(request);

        assertThat(entity.getEmail()).isNull();
    }

    @Test
    void updateEntity_isNoOp_whenRequestNull() {
        Contributor contributor = new Contributor(1L, "Original", "orig@example.com");

        mapper.updateEntity(null, contributor);

        assertThat(contributor.getName()).isEqualTo("Original");
        assertThat(contributor.getEmail()).isEqualTo("orig@example.com");
    }

    @Test
    void updateEntity_updatesAllFields() {
        Contributor contributor = new Contributor(1L, "Original", "orig@example.com");
        UpdateContributorRequest request = new UpdateContributorRequest("Renamed", "new@example.com");

        mapper.updateEntity(request, contributor);

        assertThat(contributor.getId()).isEqualTo(1L);
        assertThat(contributor.getName()).isEqualTo("Renamed");
        assertThat(contributor.getEmail()).isEqualTo("new@example.com");
    }

    @Test
    void updateEntity_preservesEmail_whenNullInRequest() {
        Contributor contributor = new Contributor(1L, "Original", "orig@example.com");
        UpdateContributorRequest request = new UpdateContributorRequest("Renamed", null);

        mapper.updateEntity(request, contributor);

        assertThat(contributor.getEmail()).isEqualTo("orig@example.com");
    }
}
