package com.orgasm.dynamo.registration;

import com.orgasm.dynamo.contributor.ContributorDynamoRepository;
import com.orgasm.dynamo.contributor.ContributorItem;
import com.orgasm.dynamo.contributor.ContributorMapper;
import com.orgasm.dynamo.organization.OrganizationDynamoRepository;
import com.orgasm.dynamo.organization.OrganizationItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinkContributorServiceTest {

    @Mock OrganizationDynamoRepository organizationRepository;
    @Mock ContributorDynamoRepository contributorRepository;

    private final ContributorMapper mapper = new ContributorMapper();
    private LinkContributorService service;

    @BeforeEach
    void setUp() {
        service = new LinkContributorService(organizationRepository, contributorRepository, mapper);
    }

    private static OrganizationItem organization(long id, String slug) {
        OrganizationItem item = new OrganizationItem();
        item.setId(id);
        item.setSlug(slug);
        return item;
    }

    @Test
    void link_throwsNotFound_whenOrganizationMissing() {
        when(organizationRepository.findBySlug("missing")).thenReturn(Optional.empty());

        var request = LinkContributorRequest.builder().organizationSlug("missing").name("Ada").build();

        assertThatThrownBy(() -> service.link(request, "sub-1", "ada@example.com"))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void link_attachesToExistingContributor_byEmailMatch() {
        when(organizationRepository.findBySlug("acme")).thenReturn(Optional.of(organization(42L, "acme")));
        var existing = new ContributorItem();
        existing.setId(1L);
        existing.setTenantId(42L);
        existing.setName("Ada");
        existing.setEmail("ada@example.com");
        when(contributorRepository.findByEmail(42L, "ada@example.com")).thenReturn(Optional.of(existing));
        when(contributorRepository.save(any(ContributorItem.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = LinkContributorRequest.builder().organizationSlug("acme").name("Ada").build();
        service.link(request, "sub-1", "ada@example.com");

        var captor = ArgumentCaptor.forClass(ContributorItem.class);
        verify(contributorRepository).save(captor.capture());
        assertThat(captor.getValue().getCognitoSub()).isEqualTo("sub-1");
        assertThat(captor.getValue().getId()).isEqualTo(1L);
    }

    @Test
    void link_throwsConflict_whenExistingContributorLinkedToDifferentAccount() {
        when(organizationRepository.findBySlug("acme")).thenReturn(Optional.of(organization(42L, "acme")));
        var existing = new ContributorItem();
        existing.setId(1L);
        existing.setTenantId(42L);
        existing.setEmail("ada@example.com");
        existing.setCognitoSub("sub-other");
        when(contributorRepository.findByEmail(42L, "ada@example.com")).thenReturn(Optional.of(existing));

        var request = LinkContributorRequest.builder().organizationSlug("acme").name("Ada").build();

        assertThatThrownBy(() -> service.link(request, "sub-1", "ada@example.com"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void link_isIdempotent_whenAlreadyLinkedToSameAccount() {
        when(organizationRepository.findBySlug("acme")).thenReturn(Optional.of(organization(42L, "acme")));
        var existing = new ContributorItem();
        existing.setId(1L);
        existing.setTenantId(42L);
        existing.setEmail("ada@example.com");
        existing.setCognitoSub("sub-1");
        when(contributorRepository.findByEmail(42L, "ada@example.com")).thenReturn(Optional.of(existing));
        when(contributorRepository.save(any(ContributorItem.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = LinkContributorRequest.builder().organizationSlug("acme").name("Ada").build();

        assertThat(service.link(request, "sub-1", "ada@example.com")).isEqualTo(mapper.toResponse(existing));
    }

    @Test
    void link_createsNewContributor_whenNoEmailMatch() {
        when(organizationRepository.findBySlug("acme")).thenReturn(Optional.of(organization(42L, "acme")));
        when(contributorRepository.findByEmail(42L, "new@example.com")).thenReturn(Optional.empty());
        when(contributorRepository.save(any(ContributorItem.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = LinkContributorRequest.builder().organizationSlug("acme").name("Newbie").build();
        service.link(request, "sub-2", "new@example.com");

        var captor = ArgumentCaptor.forClass(ContributorItem.class);
        verify(contributorRepository).save(captor.capture());
        assertThat(captor.getValue().getCognitoSub()).isEqualTo("sub-2");
        assertThat(captor.getValue().getTenantId()).isEqualTo(42L);
        assertThat(captor.getValue().getName()).isEqualTo("Newbie");
        assertThat(captor.getValue().getEmail()).isEqualTo("new@example.com");
    }
}
