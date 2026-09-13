package com.orgasm.dynamo.registration;

import com.orgasm.dynamo.contributor.ContributorResponse;
import com.orgasm.dynamo.contributor.ContributorService;
import com.orgasm.dynamo.contributor.CreateContributorRequest;
import com.orgasm.dynamo.organization.OrganizationDynamoRepository;
import com.orgasm.dynamo.organization.OrganizationItem;
import com.orgasm.dynamo.tenant.DynamoTenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock OrganizationDynamoRepository organizationRepository;
    @Mock ContributorService contributorService;
    @InjectMocks RegistrationService service;

    @AfterEach
    void clearTenant() {
        DynamoTenantContext.clear();
    }

    private static OrganizationItem organization(long id, String slug) {
        OrganizationItem item = new OrganizationItem();
        item.setId(id);
        item.setSlug(slug);
        item.setName("Org " + slug);
        return item;
    }

    @Test
    void register_setsTenantFromOrganization_andCreatesContributor() {
        when(organizationRepository.findBySlug("acme")).thenReturn(Optional.of(organization(42L, "acme")));
        var expected = ContributorResponse.builder().id("cont-1").name("Ada").build();
        when(contributorService.create(any(CreateContributorRequest.class))).thenAnswer(inv -> {
            assertThat(DynamoTenantContext.get()).isEqualTo(42L);
            return expected;
        });

        var request = RegisterContributorRequest.builder()
                .organizationSlug("acme")
                .name("Ada")
                .email("ada@example.com")
                .build();

        assertThat(service.register(request)).isEqualTo(expected);
        assertThat(DynamoTenantContext.get()).isEqualTo(1L);
    }

    @Test
    void register_throwsNotFound_whenOrganizationMissing() {
        when(organizationRepository.findBySlug("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(
                RegisterContributorRequest.builder().organizationSlug("missing").name("Ada").build()))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessageContaining("missing");
    }
}
