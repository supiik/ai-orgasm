package com.orgasm.backend.organization;

import com.orgasm.backend.config.GlobalExceptionHandler;
import com.orgasm.backend.config.VersionTestSupport;
import com.orgasm.billing.domain.Organization;
import com.orgasm.billing.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrganizationControllerTest {

    MockMvc mvc;
    OrganizationRepository repository = mock(OrganizationRepository.class);

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders
                .standaloneSetup(new OrganizationController(repository))
                .setApiVersionStrategy(VersionTestSupport.pathVersionStrategy())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void findAll_returns200_withOrganizationList() throws Exception {
        when(repository.findAll()).thenReturn(List.of(
                new Organization(1L, "default", "Default Organization"),
                new Organization(2L, "acme", "ACME Corp")));

        mvc.perform(get("/api/v1/organizations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].slug").value("default"))
                .andExpect(jsonPath("$[1].slug").value("acme"));
    }

    @Test
    void findAll_returns200_withEmptyList() throws Exception {
        when(repository.findAll()).thenReturn(List.of());

        mvc.perform(get("/api/v1/organizations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
