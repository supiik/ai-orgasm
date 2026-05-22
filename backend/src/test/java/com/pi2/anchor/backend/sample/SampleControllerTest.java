package com.pi2.anchor.backend.sample;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pi2.anchor.backend.config.GlobalExceptionHandler;
import com.pi2.anchor.backend.config.VersionTestSupport;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
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
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SampleControllerTest {

    MockMvc mvc;
    SampleService service = mock(SampleService.class);
    @SuppressWarnings("removal")
    ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new SpringDataJacksonConfiguration.PageModule(
                    new SpringDataWebSettings(EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)))
            .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class);

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders
                .standaloneSetup(new SampleController(service))
                .setApiVersionStrategy(VersionTestSupport.pathVersionStrategy())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    static SampleResponse response(String id, String name) {
        return new SampleResponse(id, name, "desc", "test@example.com", 5, 100L, 4.5,
                new BigDecimal("9.99"), true, LocalDate.of(2000, 1, 1),
                LocalDateTime.of(2024, 6, 1, 10, 0), SampleStatus.DRAFT, "notes",
                0L, Instant.EPOCH, Instant.EPOCH);
    }

    static CreateSampleRequest createRequest(String name) {
        return new CreateSampleRequest(name, "desc", null, 1, 0L, 5.0, null, true, null, null, SampleStatus.DRAFT, null);
    }

    @Test
    void findAll_returns200() throws Exception {
        when(service.findAll(any(FindSamplesRequest.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response("smpl-0001", "Widget"))));

        mvc.perform(get("/api/v1/samples"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Widget"));
    }

    @Test
    void findAll_filtersByName() throws Exception {
        when(service.findAll(eq(new FindSamplesRequest("wid", null)), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response("smpl-0001", "Widget"))));

        mvc.perform(get("/api/v1/samples").param("name", "wid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Widget"));
    }

    @Test
    void findById_returns200_whenFound() throws Exception {
        when(service.findById("smpl-0001")).thenReturn(Optional.of(response("smpl-0001", "Widget")));

        mvc.perform(get("/api/v1/samples/smpl-0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("smpl-0001"))
                .andExpect(jsonPath("$.name").value("Widget"))
                .andExpect(jsonPath("$.quantity").value(5))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void findById_returns404_whenNotFound() throws Exception {
        when(service.findById("smpl-9999")).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/samples/smpl-9999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_returns201WithLocation() throws Exception {
        when(service.create(any(CreateSampleRequest.class))).thenReturn(response("smpl-0001", "Widget"));

        mvc.perform(post("/api/v1/samples")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("Widget"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/samples/smpl-0001")))
                .andExpect(jsonPath("$.id").value("smpl-0001"));
    }

    @Test
    void create_returns400_whenNameBlank() throws Exception {
        mvc.perform(post("/api/v1/samples")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest(""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_returns200_whenFound() throws Exception {
        var updateReq = new UpdateSampleRequest("Updated", null, null, null, null, null, null, null, null, null, SampleStatus.ACTIVE, null);
        when(service.update(eq("smpl-0001"), any(UpdateSampleRequest.class))).thenReturn(response("smpl-0001", "Updated"));

        mvc.perform(put("/api/v1/samples/smpl-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated"));
    }

    @Test
    void update_returns404_whenNotFound() throws Exception {
        when(service.update(eq("smpl-9999"), any(UpdateSampleRequest.class)))
                .thenThrow(new EntityNotFoundException("Sample not found: smpl-9999"));

        mvc.perform(put("/api/v1/samples/smpl-9999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateSampleRequest("X", null, null, null, null, null, null, null, null, null, null, null))))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_returns204_whenFound() throws Exception {
        mvc.perform(delete("/api/v1/samples/smpl-0001"))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_returns404_whenNotFound() throws Exception {
        doThrow(new EntityNotFoundException("Sample not found: smpl-9999"))
                .when(service).delete("smpl-9999");

        mvc.perform(delete("/api/v1/samples/smpl-9999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void findAll_returns503_whenCircuitOpen() throws Exception {
        when(service.findAll(any(FindSamplesRequest.class), any(Pageable.class)))
                .thenThrow(mock(CallNotPermittedException.class));

        mvc.perform(get("/api/v1/samples"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void create_returns400_withFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/samples")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest(""))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void get_returns404_whenVersionUnknown() throws Exception {
        mvc.perform(get("/api/v99/samples"))
                .andExpect(status().isNotFound());
    }
}
