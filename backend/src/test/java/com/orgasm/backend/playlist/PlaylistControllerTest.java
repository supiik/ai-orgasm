package com.orgasm.backend.playlist;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orgasm.backend.config.GlobalExceptionHandler;
import com.orgasm.backend.config.VersionTestSupport;
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
                .setApiVersionStrategy(VersionTestSupport.pathVersionStrategy())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    static PlaylistResponse response(String id, String name, String description) {
        return new PlaylistResponse(id, name, description, PlaylistStatus.NEW, 0L, Instant.EPOCH, Instant.EPOCH);
    }

    @Test
    void findAll_returns200() throws Exception {
        when(service.findAll(any(FindPlaylistsRequest.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response("play-0001", "Mix", "desc"))));

        mvc.perform(get("/api/v1/playlists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Mix"));
    }

    @Test
    void findAll_filtersBy_name() throws Exception {
        when(service.findAll(eq(new FindPlaylistsRequest("mix")), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response("play-0001", "Mix", "desc"))));

        mvc.perform(get("/api/v1/playlists").param("name", "mix"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Mix"));
    }

    @Test
    void findById_returns200_whenFound() throws Exception {
        when(service.findById("play-0001")).thenReturn(Optional.of(response("play-0001", "Mix", "desc")));

        mvc.perform(get("/api/v1/playlists/play-0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("play-0001"))
                .andExpect(jsonPath("$.name").value("Mix"));
    }

    @Test
    void findById_returns404_whenNotFound() throws Exception {
        when(service.findById("play-9999")).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/playlists/play-9999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_returns201WithLocation() throws Exception {
        when(service.create(any(CreatePlaylistRequest.class))).thenReturn(response("play-0001", "New Mix", "desc"));

        mvc.perform(post("/api/v1/playlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePlaylistRequest("New Mix", "desc", null))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/playlists/play-0001")))
                .andExpect(jsonPath("$.id").value("play-0001"));
    }

    @Test
    void create_returns400_whenNameBlank() throws Exception {
        mvc.perform(post("/api/v1/playlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePlaylistRequest("", "desc", null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_returns200_whenFound() throws Exception {
        when(service.update(eq("play-0001"), any(UpdatePlaylistRequest.class))).thenReturn(response("play-0001", "Updated", "new desc"));

        mvc.perform(put("/api/v1/playlists/play-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdatePlaylistRequest("Updated", "new desc", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated"));
    }

    @Test
    void update_returns404_whenNotFound() throws Exception {
        when(service.update(eq("play-9999"), any(UpdatePlaylistRequest.class)))
                .thenThrow(new EntityNotFoundException("Playlist not found: play_9999"));

        mvc.perform(put("/api/v1/playlists/play-9999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdatePlaylistRequest("X", null, null))))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_returns204_whenFound() throws Exception {
        mvc.perform(delete("/api/v1/playlists/play-0001"))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_returns404_whenNotFound() throws Exception {
        doThrow(new EntityNotFoundException("Playlist not found: play_9999"))
                .when(service).delete("play-9999");

        mvc.perform(delete("/api/v1/playlists/play-9999"))
                .andExpect(status().isNotFound());
    }
}
