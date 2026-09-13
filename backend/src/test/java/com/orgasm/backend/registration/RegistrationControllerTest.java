package com.orgasm.backend.registration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orgasm.backend.config.GlobalExceptionHandler;
import com.orgasm.backend.config.VersionTestSupport;
import com.orgasm.backend.contributor.ContributorResponse;
import com.orgasm.backend.contributor.ContributorService;
import com.orgasm.billing.domain.Organization;
import com.orgasm.billing.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Optional;
import java.util.function.UnaryOperator;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RegistrationControllerTest {

    MockMvc mvc;
    OrganizationRepository orgRepository = mock(OrganizationRepository.class);
    ContributorService contributorService = mock(ContributorService.class);
    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders
                .standaloneSetup(new RegistrationController(orgRepository, contributorService))
                .setApiVersionStrategy(VersionTestSupport.pathVersionStrategy())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    static ContributorResponse contributor(String id, String name) {
        return new ContributorResponse(id, name, null, null, 0L, Instant.EPOCH, Instant.EPOCH);
    }

    @Test
    @SuppressWarnings("unchecked")
    void register_returns201WithLocation() throws Exception {
        when(orgRepository.findBySlug("default"))
                .thenReturn(Optional.of(new Organization(1L, "default", "Default Organization", null)));
        when(contributorService.create(any(UnaryOperator.class)))
                .thenReturn(contributor("cont-abc123", "Alice"));

        mvc.perform(post("/api/v1/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegistrationController.RegisterRequest("Alice", "alice@example.com", null, "default"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/contributors/cont-abc123")))
                .andExpect(jsonPath("$.id").value("cont-abc123"))
                .andExpect(jsonPath("$.name").value("Alice"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void register_returns201_whenEmailMatchesAllowedDomain() throws Exception {
        when(orgRepository.findBySlug("acme"))
                .thenReturn(Optional.of(new Organization(2L, "acme", "ACME Corp", "acme.com")));
        when(contributorService.create(any(UnaryOperator.class)))
                .thenReturn(contributor("cont-abc123", "Alice"));

        mvc.perform(post("/api/v1/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegistrationController.RegisterRequest("Alice", "alice@acme.com", null, "acme"))))
                .andExpect(status().isCreated());
    }

    @Test
    void register_returns400_whenEmailDomainDoesNotMatchAllowedDomain() throws Exception {
        when(orgRepository.findBySlug("acme"))
                .thenReturn(Optional.of(new Organization(2L, "acme", "ACME Corp", "acme.com")));

        mvc.perform(post("/api/v1/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegistrationController.RegisterRequest("Alice", "alice@gmail.com", null, "acme"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_returns400_whenAllowedDomainSetAndEmailMissing() throws Exception {
        when(orgRepository.findBySlug("acme"))
                .thenReturn(Optional.of(new Organization(2L, "acme", "ACME Corp", "acme.com")));

        mvc.perform(post("/api/v1/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegistrationController.RegisterRequest("Alice", null, null, "acme"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_returns404_whenOrgNotFound() throws Exception {
        when(orgRepository.findBySlug("unknown")).thenReturn(Optional.empty());

        mvc.perform(post("/api/v1/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegistrationController.RegisterRequest("Alice", null, null, "unknown"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void register_returns400_whenNameBlank() throws Exception {
        mvc.perform(post("/api/v1/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegistrationController.RegisterRequest("", null, null, "default"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_returns400_whenOrgSlugBlank() throws Exception {
        mvc.perform(post("/api/v1/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegistrationController.RegisterRequest("Alice", null, null, ""))))
                .andExpect(status().isBadRequest());
    }
}
