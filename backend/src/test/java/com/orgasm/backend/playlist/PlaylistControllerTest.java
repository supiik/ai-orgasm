package com.orgasm.backend.playlist;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orgasm.backend.config.GlobalExceptionHandler;
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

class PlaylistControllerTest {

    MockMvc mvc;
    PlaylistService service = mock(PlaylistService.class);
    ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new SpringDataJacksonConfiguration.PageModule(
                    new SpringDataWebSettings(EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)));

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders
                .standaloneSetup(new PlaylistController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    static PlaylistResponse response(Long id, String name, String description) {
        return new PlaylistResponse(id, name, description, 0L, Instant.EPOCH, Instant.EPOCH);
    }

    @Test
    void findAll_returns200() throws Exception {
        when(service.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response(1L, "Mix", "desc"))));

        mvc.perform(get("/api/playlists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Mix"));
    }

    @Test
    void findById_returns200_whenFound() throws Exception {
        when(service.findById(1L)).thenReturn(Optional.of(response(1L, "Mix", "desc")));

        mvc.perform(get("/api/playlists/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Mix"));
    }

    @Test
    void findById_returns404_whenNotFound() throws Exception {
        when(service.findById(99L)).thenReturn(Optional.empty());

        mvc.perform(get("/api/playlists/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_returns201WithLocation() throws Exception {
        when(service.create(any())).thenReturn(response(1L, "New Mix", "desc"));

        mvc.perform(post("/api/playlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePlaylistRequest("New Mix", "desc"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/playlists/1")))
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void create_returns400_whenNameBlank() throws Exception {
        mvc.perform(post("/api/playlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePlaylistRequest("", "desc"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_returns200_whenFound() throws Exception {
        when(service.update(eq(1L), any())).thenReturn(response(1L, "Updated", "new desc"));

        mvc.perform(put("/api/playlists/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdatePlaylistRequest("Updated", "new desc"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated"));
    }

    @Test
    void update_returns404_whenNotFound() throws Exception {
        when(service.update(eq(99L), any()))
                .thenThrow(new EntityNotFoundException("Playlist not found: 99"));

        mvc.perform(put("/api/playlists/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdatePlaylistRequest("X", null))))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_returns204_whenFound() throws Exception {
        mvc.perform(delete("/api/playlists/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_returns404_whenNotFound() throws Exception {
        doThrow(new EntityNotFoundException("Playlist not found: 99"))
                .when(service).delete(99L);

        mvc.perform(delete("/api/playlists/99"))
                .andExpect(status().isNotFound());
    }
}
