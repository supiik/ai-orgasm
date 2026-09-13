package com.orgasm.dynamo.organization;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock
    OrganizationDynamoRepository repository;

    @InjectMocks
    OrganizationService service;

    @Test
    void findAll_mapsItemsToResponses() {
        OrganizationItem item1 = new OrganizationItem();
        item1.setId(1L);
        item1.setSlug("acme");
        item1.setName("Acme Inc");

        OrganizationItem item2 = new OrganizationItem();
        item2.setId(2L);
        item2.setSlug("globex");
        item2.setName("Globex Corp");

        when(repository.findAll()).thenReturn(List.of(item1, item2));

        List<OrganizationResponse> result = service.findAll();

        assertThat(result).containsExactly(
                new OrganizationResponse(1L, "acme", "Acme Inc"),
                new OrganizationResponse(2L, "globex", "Globex Corp"));
    }

    @Test
    void findBySlug_returnsItem_whenPresent() {
        OrganizationItem item = new OrganizationItem();
        item.setId(1L);
        item.setSlug("acme");
        item.setName("Acme Inc");

        when(repository.findBySlug("acme")).thenReturn(Optional.of(item));

        Optional<OrganizationItem> result = service.findBySlug("acme");

        assertThat(result).contains(item);
    }

    @Test
    void findBySlug_returnsEmpty_whenAbsent() {
        when(repository.findBySlug("unknown")).thenReturn(Optional.empty());

        assertThat(service.findBySlug("unknown")).isEmpty();
    }
}
