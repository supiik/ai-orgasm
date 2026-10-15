package com.orgasm.backend.organization;

import com.orgasm.backend.config.GlobalExceptionHandler;
import com.orgasm.backend.config.VersionTestSupport;
import com.orgasm.backend.contributor.ContributorService;
import com.orgasm.backend.tenant.TenantContext;
import com.orgasm.billing.domain.Organization;
import com.orgasm.billing.repository.OrganizationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrganizationControllerTest {

    MockMvc mvc;
    OrganizationRepository repository = mock(OrganizationRepository.class);
    ContributorService contributorService = mock(ContributorService.class);

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders
                .standaloneSetup(new OrganizationController(repository, contributorService))
                .setApiVersionStrategy(VersionTestSupport.pathVersionStrategy())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                Jwt.withTokenValue("t").header("alg", "none")
                        .subject("sub-1")
                        .claim("email", "admin@example.com")
                        .build()));
        TenantContext.set(1L);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void findAll_returns200_withOrganizationList() throws Exception {
        when(repository.findAll()).thenReturn(List.of(
                new Organization(1L, "default", "Default Organization", null, null),
                new Organization(2L, "acme", "ACME Corp", null, null)));

        mvc.perform(get("/api/v1/organizations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].slug").value("default"))
                .andExpect(jsonPath("$[1].slug").value("acme"));
    }

    @Test
    void findAll_doesNotExposeAllowedDomain() throws Exception {
        when(repository.findAll()).thenReturn(List.of(
                new Organization(1L, "acme", "ACME Corp", "acme.com", null)));

        mvc.perform(get("/api/v1/organizations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].allowedDomain").doesNotExist());
    }

    @Test
    void findAll_returns200_withEmptyList() throws Exception {
        when(repository.findAll()).thenReturn(List.of());

        mvc.perform(get("/api/v1/organizations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void findByInviteToken_returns200_withOrgSummary() throws Exception {
        when(repository.findByInviteToken("tok-123"))
                .thenReturn(Optional.of(new Organization(1L, "acme", "ACME Corp", null, "tok-123")));

        mvc.perform(get("/api/v1/organizations/by-invite/tok-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("acme"))
                .andExpect(jsonPath("$.name").value("ACME Corp"));
    }

    @Test
    void findByInviteToken_returns404_whenTokenUnknown() throws Exception {
        when(repository.findByInviteToken("bad-token")).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/organizations/by-invite/bad-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getInviteLink_returns200_withCurrentToken() throws Exception {
        when(contributorService.isOrgAdmin("admin@example.com")).thenReturn(true);
        when(repository.findById(1L)).thenReturn(Optional.of(new Organization(1L, "acme", "ACME Corp", null, "tok-123")));

        mvc.perform(get("/api/v1/organizations/invite-link"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inviteToken").value("tok-123"));
    }

    @Test
    void getInviteLink_returns403_whenActorIsNotOrgAdmin() throws Exception {
        when(contributorService.isOrgAdmin("admin@example.com")).thenReturn(false);

        mvc.perform(get("/api/v1/organizations/invite-link"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createInviteLink_returns200_withNewToken() throws Exception {
        when(contributorService.isOrgAdmin("admin@example.com")).thenReturn(true);
        var org = new Organization(1L, "acme", "ACME Corp", null, null);
        when(repository.findById(1L)).thenReturn(Optional.of(org));
        when(repository.save(any(Organization.class))).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(post("/api/v1/organizations/invite-link"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inviteToken").isNotEmpty());
    }

    @Test
    void revokeInviteLink_returns204_andClearsToken() throws Exception {
        when(contributorService.isOrgAdmin("admin@example.com")).thenReturn(true);
        var org = new Organization(1L, "acme", "ACME Corp", null, "tok-123");
        when(repository.findById(1L)).thenReturn(Optional.of(org));

        mvc.perform(delete("/api/v1/organizations/invite-link"))
                .andExpect(status().isNoContent());

        assertThat(org.getInviteToken()).isNull();
    }
}
