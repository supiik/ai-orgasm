package com.orgasm.dynamo.contributor;

import com.orgasm.dynamo.domain.IdGenerator;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ContributorMapperTest {

    private final ContributorMapper mapper = new ContributorMapper();

    @Test
    void toItem_populatesKeysAndFields() {
        var request = new CreateContributorRequest("Jane Doe", "jane@example.com", "http://example.com/a.png");

        var item = mapper.toItem(request, 7L, 42L);

        assertThat(item.getPk()).isEqualTo("7#CONTRIBUTOR");
        assertThat(item.getSk()).isEqualTo("42");
        assertThat(item.getId()).isEqualTo(42L);
        assertThat(item.getTenantId()).isEqualTo(7L);
        assertThat(item.getName()).isEqualTo("Jane Doe");
        assertThat(item.getEmail()).isEqualTo("jane@example.com");
        assertThat(item.getAvatarUrl()).isEqualTo("http://example.com/a.png");
    }

    @Test
    void toItem_allowsNullOptionalFields() {
        var request = new CreateContributorRequest("Jane Doe", null, null);

        var item = mapper.toItem(request, 1L, 1L);

        assertThat(item.getEmail()).isNull();
        assertThat(item.getAvatarUrl()).isNull();
    }

    @Test
    void toResponse_mapsFieldsAndFormatsId() {
        var item = new ContributorItem();
        item.setId(42L);
        item.setName("Jane Doe");
        item.setEmail("jane@example.com");
        item.setAvatarUrl("http://example.com/a.png");
        item.setVersion(3L);
        item.setCreatedAt(Instant.EPOCH);
        item.setUpdatedAt(Instant.EPOCH);

        var response = mapper.toResponse(item);

        assertThat(response.id()).isEqualTo(IdGenerator.format("cont", 42L));
        assertThat(response.name()).isEqualTo("Jane Doe");
        assertThat(response.email()).isEqualTo("jane@example.com");
        assertThat(response.avatarUrl()).isEqualTo("http://example.com/a.png");
        assertThat(response.version()).isEqualTo(3L);
        assertThat(response.createdAt()).isEqualTo(Instant.EPOCH);
        assertThat(response.updatedAt()).isEqualTo(Instant.EPOCH);
    }

    @Test
    void toResponse_handlesNullOptionalFields() {
        var item = new ContributorItem();
        item.setId(1L);

        var response = mapper.toResponse(item);

        assertThat(response.email()).isNull();
        assertThat(response.avatarUrl()).isNull();
    }

    @Test
    void updateItem_overwritesRequiredField_andOnlyOverwritesOptionalFieldsWhenPresent() {
        var existing = new ContributorItem();
        existing.setName("Old");
        existing.setEmail("old@example.com");
        existing.setAvatarUrl("http://example.com/old.png");

        mapper.updateItem(new UpdateContributorRequest("New", null, null), existing);

        assertThat(existing.getName()).isEqualTo("New");
        assertThat(existing.getEmail()).isEqualTo("old@example.com");
        assertThat(existing.getAvatarUrl()).isEqualTo("http://example.com/old.png");
    }

    @Test
    void updateItem_overwritesOptionalFields_whenPresent() {
        var existing = new ContributorItem();
        existing.setName("Old");
        existing.setEmail("old@example.com");
        existing.setAvatarUrl("http://example.com/old.png");

        mapper.updateItem(new UpdateContributorRequest("New", "new@example.com", "http://example.com/new.png"), existing);

        assertThat(existing.getName()).isEqualTo("New");
        assertThat(existing.getEmail()).isEqualTo("new@example.com");
        assertThat(existing.getAvatarUrl()).isEqualTo("http://example.com/new.png");
    }
}
