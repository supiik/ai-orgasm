package com.etactics.proxima.service.backend.contributor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.etactics.proxima.service.backend.config.GlobalExceptionHandler;
import com.etactics.proxima.service.backend.config.VersionTestSupport;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.SpringDataJacksonConfiguration;
import org.springframework.data.web.config.SpringDataWebSettings;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ContributorControllerTest {

    MockMvc mvc;
    ContributorService service = mock(ContributorService.class);
    ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new SpringDataJacksonConfiguration.PageModule(
                    new SpringDataWebSettings(EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)));

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders
                .standaloneSetup(new ContributorController(service))
                .setApiVersionStrategy(VersionTestSupport.pathVersionStrategy())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    static ContributorResponse response(String id, String name) {
        return new ContributorResponse(id, name, null, null, 0L, Instant.EPOCH, Instant.EPOCH);
    }

    @Test
    void findAll_returns200() throws Exception {
        when(service.findAll(any(FindContributorsRequest.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response("cont-0001", "Alice"))));

        mvc.perform(get("/api/v1/contributors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Alice"));
    }

    @Test
    void findAll_filtersBy_name() throws Exception {
        when(service.findAll(eq(new FindContributorsRequest("ali")), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response("cont-0001", "Alice"))));

        mvc.perform(get("/api/v1/contributors").param("name", "ali"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Alice"));
    }

    @Test
    void findById_returns200_whenFound() throws Exception {
        when(service.findById("cont-0001")).thenReturn(Optional.of(response("cont-0001", "Alice")));

        mvc.perform(get("/api/v1/contributors/cont-0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("cont-0001"))
                .andExpect(jsonPath("$.name").value("Alice"));
    }

    @Test
    void findById_returns404_whenNotFound() throws Exception {
        when(service.findById("cont-9999")).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/contributors/cont-9999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_returns201WithLocation() throws Exception {
        when(service.create(any(CreateContributorRequest.class))).thenReturn(response("cont-0001", "Alice"));

        mvc.perform(post("/api/v1/contributors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateContributorRequest("Alice", null, null))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/contributors/cont-0001")))
                .andExpect(jsonPath("$.id").value("cont-0001"));
    }

    @Test
    void create_returns400_whenNameBlank() throws Exception {
        mvc.perform(post("/api/v1/contributors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateContributorRequest("", null, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_returns200_whenFound() throws Exception {
        when(service.update(eq("cont-0001"), any(UpdateContributorRequest.class))).thenReturn(response("cont-0001", "Bob"));

        mvc.perform(put("/api/v1/contributors/cont-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateContributorRequest("Bob", null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Bob"));
    }

    @Test
    void update_returns404_whenNotFound() throws Exception {
        when(service.update(eq("cont-9999"), any(UpdateContributorRequest.class)))
                .thenThrow(new EntityNotFoundException("Contributor not found: cont_9999"));

        mvc.perform(put("/api/v1/contributors/cont-9999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateContributorRequest("X", null, null))))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_returns204_whenFound() throws Exception {
        mvc.perform(delete("/api/v1/contributors/cont-0001"))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_returns404_whenNotFound() throws Exception {
        doThrow(new EntityNotFoundException("Contributor not found: cont_9999"))
                .when(service).delete("cont-9999");

        mvc.perform(delete("/api/v1/contributors/cont-9999"))
                .andExpect(status().isNotFound());
    }
}
