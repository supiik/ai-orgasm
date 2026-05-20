package com.orgasm.backend.contributor;

import com.orgasm.backend.domain.IdGenerator;
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
        Contributor contributor = new Contributor(7L, null, "Alice", "alice@example.com", "https://example.com/alice.jpg");

        ContributorResponse response = mapper.toResponse(contributor);

        assertThat(response).isNotNull();
        assertThat(response.id()).startsWith("cont-");
        assertThat(IdGenerator.parse(response.id())).isEqualTo(7L);
        assertThat(response.name()).isEqualTo("Alice");
        assertThat(response.email()).isEqualTo("alice@example.com");
        assertThat(response.avatarUrl()).isEqualTo("https://example.com/alice.jpg");
    }

    @Test
    void toEntity_returnsNull_whenInputNull() {
        assertThat(mapper.toEntity(null)).isNull();
    }

    @Test
    void toEntity_mapsAllFields() {
        CreateContributorRequest request = new CreateContributorRequest("Bob", "bob@example.com", "https://example.com/bob.jpg");

        Contributor entity = mapper.toEntity(request);

        assertThat(entity).isNotNull();
        assertThat(entity.getName()).isEqualTo("Bob");
        assertThat(entity.getEmail()).isEqualTo("bob@example.com");
        assertThat(entity.getAvatarUrl()).isEqualTo("https://example.com/bob.jpg");
    }

    @Test
    void toEntity_handlesNullEmail() {
        CreateContributorRequest request = new CreateContributorRequest("Bob", null, null);

        Contributor entity = mapper.toEntity(request);

        assertThat(entity.getEmail()).isNull();
        assertThat(entity.getAvatarUrl()).isNull();
    }

    @Test
    void updateEntity_isNoOp_whenRequestNull() {
        Contributor contributor = new Contributor(1L, null, "Original", "orig@example.com", null);

        mapper.updateEntity(null, contributor);

        assertThat(contributor.getName()).isEqualTo("Original");
        assertThat(contributor.getEmail()).isEqualTo("orig@example.com");
    }

    @Test
    void updateEntity_updatesAllFields() {
        Contributor contributor = new Contributor(1L, null, "Original", "orig@example.com", null);
        UpdateContributorRequest request = new UpdateContributorRequest("Renamed", "new@example.com", "https://example.com/new.jpg");

        mapper.updateEntity(request, contributor);

        assertThat(contributor.getId()).isEqualTo(1L);
        assertThat(contributor.getName()).isEqualTo("Renamed");
        assertThat(contributor.getEmail()).isEqualTo("new@example.com");
        assertThat(contributor.getAvatarUrl()).isEqualTo("https://example.com/new.jpg");
    }

    @Test
    void updateEntity_preservesEmail_whenNullInRequest() {
        Contributor contributor = new Contributor(1L, null, "Original", "orig@example.com", "https://example.com/orig.jpg");
        UpdateContributorRequest request = new UpdateContributorRequest("Renamed", null, null);

        mapper.updateEntity(request, contributor);

        assertThat(contributor.getEmail()).isEqualTo("orig@example.com");
        assertThat(contributor.getAvatarUrl()).isEqualTo("https://example.com/orig.jpg");
    }
}
