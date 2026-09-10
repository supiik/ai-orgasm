package com.orgasm.dynamo.contributor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContributorServiceTest {

    @Mock ContributorDynamoRepository repository;
    @Mock ContributorMapper mapper;
    @InjectMocks ContributorService service;

    private static ContributorItem item(long id, String name) {
        ContributorItem item = new ContributorItem();
        item.setId(id);
        item.setTenantId(1L);
        item.setName(name);
        return item;
    }

    private static ContributorResponse response(String id, String name) {
        return ContributorResponse.builder().id(id).name(name).build();
    }

    @Test
    void create_savesAndReturnsResponse() {
        var request = new CreateContributorRequest("Jane Doe", "jane@example.com", null);
        var expected = response("cont-1", "Jane Doe");
        when(mapper.toItem(any(CreateContributorRequest.class), org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(inv -> item(inv.getArgument(2), "Jane Doe"));
        when(repository.save(any(ContributorItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(ContributorItem.class))).thenReturn(expected);

        assertThat(service.create(request)).isEqualTo(expected);
    }

    @Test
    void create_savesAndReturnsResponse_viaBuilder() {
        var expected = response("cont-1", "Jane Doe");
        when(mapper.toItem(any(CreateContributorRequest.class), org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(inv -> item(inv.getArgument(2), "Jane Doe"));
        when(repository.save(any(ContributorItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(ContributorItem.class))).thenReturn(expected);

        assertThat(service.create(b -> b.name("Jane Doe").email("jane@example.com"))).isEqualTo(expected);
    }

    @Test
    void findById_returnsResponse_whenExists() {
        var stored = item(1L, "Jane Doe");
        var expected = response("cont-1", "Jane Doe");
        when(repository.findById(1L, 1L)).thenReturn(Optional.of(stored));
        when(mapper.toResponse(stored)).thenReturn(expected);

        assertThat(service.findById(com.orgasm.dynamo.domain.IdGenerator.format("cont", 1L))).contains(expected);
    }

    @Test
    void findById_returnsEmpty_whenNotFound() {
        when(repository.findById(1L, 1L)).thenReturn(Optional.empty());

        assertThat(service.findById(com.orgasm.dynamo.domain.IdGenerator.format("cont", 1L))).isEmpty();
    }

    @Test
    void findById_returnsEmpty_whenSoftDeleted() {
        var stored = item(1L, "Jane Doe");
        stored.setDeletedAt(Instant.now());
        when(repository.findById(1L, 1L)).thenReturn(Optional.of(stored));

        assertThat(service.findById(com.orgasm.dynamo.domain.IdGenerator.format("cont", 1L))).isEmpty();
    }

    @Test
    void findAll_filtersOutSoftDeleted_andPaginates() {
        var kept = item(1L, "Jane");
        var deleted = item(2L, "Gone");
        deleted.setDeletedAt(Instant.now());
        when(repository.findAllByTenant(1L)).thenReturn(List.of(kept, deleted));
        when(mapper.toResponse(kept)).thenReturn(response("cont-1", "Jane"));

        Page<ContributorResponse> result = service.findAll(FindContributorsRequest.builder().build(), Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(response("cont-1", "Jane"));
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void findAll_filtersByName() {
        var jane = item(1L, "Jane Doe");
        var john = item(2L, "John Smith");
        when(repository.findAllByTenant(1L)).thenReturn(List.of(jane, john));
        when(mapper.toResponse(jane)).thenReturn(response("cont-1", "Jane Doe"));

        Page<ContributorResponse> result = service.findAll(new FindContributorsRequest("jane"), Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(response("cont-1", "Jane Doe"));
    }

    @Test
    void findAll_filtersByName_viaBuilder() {
        var jane = item(1L, "Jane Doe");
        when(repository.findAllByTenant(1L)).thenReturn(List.of(jane));
        when(mapper.toResponse(jane)).thenReturn(response("cont-1", "Jane Doe"));

        Page<ContributorResponse> result = service.findAll(b -> b.name("jane"), Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(response("cont-1", "Jane Doe"));
    }

    @Test
    void findAll_respectsPageSize() {
        var first = item(1L, "A");
        var second = item(2L, "B");
        when(repository.findAllByTenant(1L)).thenReturn(List.of(first, second));
        when(mapper.toResponse(first)).thenReturn(response("cont-1", "A"));

        Page<ContributorResponse> result = service.findAll(FindContributorsRequest.builder().build(), PageRequest.of(0, 1));

        assertThat(result.getContent()).containsExactly(response("cont-1", "A"));
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    private static String externalId(long id) {
        return com.orgasm.dynamo.domain.IdGenerator.format("cont", id);
    }

    @Test
    void update_appliesMappingAndReturnsResponse() {
        var existing = item(1L, "Old");
        var expected = response(externalId(1L), "New");
        when(repository.findById(1L, 1L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);
        when(mapper.toResponse(existing)).thenReturn(expected);

        assertThat(service.update(externalId(1L), new UpdateContributorRequest("New", "new@example.com", null)))
                .isEqualTo(expected);
        verify(mapper).updateItem(any(UpdateContributorRequest.class), org.mockito.ArgumentMatchers.eq(existing));
    }

    @Test
    void update_appliesMappingAndReturnsResponse_viaBuilder() {
        var existing = item(1L, "Old");
        var expected = response(externalId(1L), "New");
        when(repository.findById(1L, 1L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);
        when(mapper.toResponse(existing)).thenReturn(expected);

        assertThat(service.update(externalId(1L), b -> b.name("New").email("new@example.com"))).isEqualTo(expected);
    }

    @Test
    void update_throwsNotFound_whenMissing() {
        when(repository.findById(1L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(externalId(1L), new UpdateContributorRequest("X", null, null)))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessageContaining(externalId(1L));
    }

    @Test
    void update_throwsNotFound_whenSoftDeleted() {
        var deleted = item(1L, "Old");
        deleted.setDeletedAt(Instant.now());
        when(repository.findById(1L, 1L)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.update(externalId(1L), new UpdateContributorRequest("X", null, null)))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void delete_softDeletes_whenExists() {
        var existing = item(1L, "Old");
        when(repository.findById(1L, 1L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        service.delete(externalId(1L));

        assertThat(existing.getDeletedAt()).isNotNull();
        verify(repository).save(existing);
    }

    @Test
    void delete_throwsNotFound_whenMissing() {
        when(repository.findById(1L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(externalId(1L)))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessageContaining(externalId(1L));
    }
}
