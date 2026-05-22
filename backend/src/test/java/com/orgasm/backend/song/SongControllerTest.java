package com.orgasm.backend.song;

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

class SongControllerTest {

    MockMvc mvc;
    SongService service = mock(SongService.class);
    ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new SpringDataJacksonConfiguration.PageModule(
                    new SpringDataWebSettings(EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)));

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders
                .standaloneSetup(new SongController(service))
                .setApiVersionStrategy(VersionTestSupport.pathVersionStrategy())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    static SongResponse response(String id, String artist, String name) {
        return new SongResponse(id, artist, name, null, null, null, 0L, Instant.EPOCH, Instant.EPOCH);
    }

    @Test
    void findAll_returns200() throws Exception {
        when(service.findAll(any(FindSongsRequest.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response("song-0001", "Radiohead", "Creep"))));

        mvc.perform(get("/api/v1/songs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].artist").value("Radiohead"))
                .andExpect(jsonPath("$.content[0].name").value("Creep"));
    }

    @Test
    void findAll_filtersBy_name() throws Exception {
        when(service.findAll(eq(new FindSongsRequest("cree")), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response("song-0001", "Radiohead", "Creep"))));

        mvc.perform(get("/api/v1/songs").param("name", "cree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Creep"));
    }

    @Test
    void findById_returns200_whenFound() throws Exception {
        when(service.findById("song-0001")).thenReturn(Optional.of(response("song-0001", "Radiohead", "Creep")));

        mvc.perform(get("/api/v1/songs/song-0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("song-0001"))
                .andExpect(jsonPath("$.artist").value("Radiohead"));
    }

    @Test
    void findById_returns404_whenNotFound() throws Exception {
        when(service.findById("song-9999")).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/songs/song-9999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_returns201WithLocation() throws Exception {
        when(service.create(any(CreateSongRequest.class))).thenReturn(response("song-0001", "Radiohead", "Creep"));

        mvc.perform(post("/api/v1/songs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateSongRequest("Radiohead", "Creep", "Pablo Honey", 1993, null))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/songs/song-0001")))
                .andExpect(jsonPath("$.id").value("song-0001"));
    }

    @Test
    void create_returns400_whenArtistBlank() throws Exception {
        mvc.perform(post("/api/v1/songs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateSongRequest("", "Creep", null, null, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_returns400_whenNameBlank() throws Exception {
        mvc.perform(post("/api/v1/songs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateSongRequest("Radiohead", "", null, null, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_returns200_whenFound() throws Exception {
        when(service.update(eq("song-0001"), any(UpdateSongRequest.class))).thenReturn(response("song-0001", "Radiohead", "Karma Police"));

        mvc.perform(put("/api/v1/songs/song-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateSongRequest("Radiohead", "Karma Police", "OK Computer", 1997, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Karma Police"));
    }

    @Test
    void update_returns404_whenNotFound() throws Exception {
        when(service.update(eq("song-9999"), any(UpdateSongRequest.class)))
                .thenThrow(new EntityNotFoundException("Song not found: song_9999"));

        mvc.perform(put("/api/v1/songs/song-9999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateSongRequest("X", "Y", null, null, null))))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_returns204_whenFound() throws Exception {
        mvc.perform(delete("/api/v1/songs/song-0001"))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_returns404_whenNotFound() throws Exception {
        doThrow(new EntityNotFoundException("Song not found: song_9999"))
                .when(service).delete("song-9999");

        mvc.perform(delete("/api/v1/songs/song-9999"))
                .andExpect(status().isNotFound());
    }
}
