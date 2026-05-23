package com.orgasm.backend.orgasm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orgasm.backend.config.GlobalExceptionHandler;
import com.orgasm.backend.config.VersionTestSupport;
import com.orgasm.backend.nomination.NominationResponse;
import com.orgasm.backend.nomination.NominationStatus;
import com.orgasm.backend.playlist.PlaylistResponse;
import com.orgasm.backend.playlist.PlaylistStatus;
import com.orgasm.backend.result.GuessingResultNotifier;
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

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrgasmControllerTest {

    MockMvc mvc;
    OrgasmService service = mock(OrgasmService.class);
    GuessingResultNotifier guessingResultNotifier = mock(GuessingResultNotifier.class);

    @SuppressWarnings("removal")
    ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new SpringDataJacksonConfiguration.PageModule(
                    new SpringDataWebSettings(EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)))
            .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class);

    static final String PLAYLIST_ID    = "play-0000000000000001";
    static final String CONTRIBUTOR_ID = "cont-0000000000000002";
    static final String SONG_ID        = "song-0000000000000003";
    static final String NOMINATION_ID  = "nom-0000000000000004";
    static final Instant FUTURE        = Instant.now().plusSeconds(3600);

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders
                .standaloneSetup(new OrgasmController(service, guessingResultNotifier))
                .setApiVersionStrategy(VersionTestSupport.pathVersionStrategy())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    static PlaylistResponse playlistResponse() {
        return new PlaylistResponse(PLAYLIST_ID, "Mix", null, PlaylistStatus.OPEN,
                CONTRIBUTOR_ID, null, null, Instant.EPOCH, null, 0L, Instant.EPOCH, Instant.EPOCH);
    }

    static NominationResponse nominationResponse() {
        return NominationResponse.builder()
                .id(NOMINATION_ID).playlistId(PLAYLIST_ID).songId(SONG_ID)
                .nominatedById(CONTRIBUTOR_ID).status(NominationStatus.PENDING).build();
    }

    // ── openPlaylist ──────────────────────────────────────────────────────────

    @Test
    void openPlaylist_returns200() throws Exception {
        when(service.openPlaylist(eq(PLAYLIST_ID), any(OpenPlaylistRequest.class)))
                .thenReturn(playlistResponse());

        mvc.perform(post("/api/v1/playlists/{id}/open", PLAYLIST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new OpenPlaylistRequest(CONTRIBUTOR_ID, FUTURE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PLAYLIST_ID));
    }

    @Test
    void openPlaylist_returns409_whenConflict() throws Exception {
        when(service.openPlaylist(eq(PLAYLIST_ID), any(OpenPlaylistRequest.class)))
                .thenThrow(new IllegalStateException("Only NEW playlists can be opened"));

        mvc.perform(post("/api/v1/playlists/{id}/open", PLAYLIST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new OpenPlaylistRequest(CONTRIBUTOR_ID, FUTURE))))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void openPlaylist_returns400_whenBodyInvalid() throws Exception {
        mvc.perform(post("/api/v1/playlists/{id}/open", PLAYLIST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contributorId\":\"\",\"deadline\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void openPlaylist_returns404_whenPlaylistMissing() throws Exception {
        when(service.openPlaylist(eq(PLAYLIST_ID), any(OpenPlaylistRequest.class)))
                .thenThrow(new EntityNotFoundException("Playlist not found"));

        mvc.perform(post("/api/v1/playlists/{id}/open", PLAYLIST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new OpenPlaylistRequest(CONTRIBUTOR_ID, FUTURE))))
                .andExpect(status().isNotFound());
    }

    // ── findPlaylistsByContributor ────────────────────────────────────────────

    @Test
    void findPlaylistsByContributor_returns200() throws Exception {
        when(service.findPlaylistsByContributor(eq(CONTRIBUTOR_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(playlistResponse())));

        mvc.perform(get("/api/v1/contributors/{id}/playlists", CONTRIBUTOR_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(PLAYLIST_ID));
    }

    // ── nominateSong ──────────────────────────────────────────────────────────

    @Test
    void nominateSong_returns201() throws Exception {
        when(service.nominateSong(eq(PLAYLIST_ID), any(NominateSongRequest.class)))
                .thenReturn(nominationResponse());

        mvc.perform(post("/api/v1/playlists/{id}/nominations", PLAYLIST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new NominateSongRequest(CONTRIBUTOR_ID, SONG_ID))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(NOMINATION_ID));
    }

    @Test
    void nominateSong_returns409_whenAlreadyNominated() throws Exception {
        when(service.nominateSong(eq(PLAYLIST_ID), any(NominateSongRequest.class)))
                .thenThrow(new IllegalStateException("Song already nominated"));

        mvc.perform(post("/api/v1/playlists/{id}/nominations", PLAYLIST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new NominateSongRequest(CONTRIBUTOR_ID, SONG_ID))))
                .andExpect(status().isConflict());
    }

    @Test
    void nominateSong_returns400_whenBodyInvalid() throws Exception {
        mvc.perform(post("/api/v1/playlists/{id}/nominations", PLAYLIST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contributorId\":\"\",\"songId\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── findNominations ───────────────────────────────────────────────────────

    @Test
    void findNominations_returns200() throws Exception {
        when(service.findNominations(eq(PLAYLIST_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(nominationResponse())));

        mvc.perform(get("/api/v1/playlists/{id}/nominations", PLAYLIST_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(NOMINATION_ID));
    }

    // ── approveNomination ─────────────────────────────────────────────────────

    @Test
    void approveNomination_returns200() throws Exception {
        var approved = NominationResponse.builder()
                .id(NOMINATION_ID).status(NominationStatus.APPROVED).build();
        when(service.approveNomination(NOMINATION_ID, CONTRIBUTOR_ID)).thenReturn(approved);

        mvc.perform(put("/api/v1/nominations/{id}/approve", NOMINATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ReviewNominationRequest(CONTRIBUTOR_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void approveNomination_returns409_whenNotPending() throws Exception {
        when(service.approveNomination(eq(NOMINATION_ID), eq(CONTRIBUTOR_ID)))
                .thenThrow(new IllegalStateException("Nomination is not pending"));

        mvc.perform(put("/api/v1/nominations/{id}/approve", NOMINATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ReviewNominationRequest(CONTRIBUTOR_ID))))
                .andExpect(status().isConflict());
    }

    @Test
    void approveNomination_returns400_whenBodyInvalid() throws Exception {
        mvc.perform(put("/api/v1/nominations/{id}/approve", NOMINATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewerId\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── declineNomination ─────────────────────────────────────────────────────

    @Test
    void declineNomination_returns200() throws Exception {
        var declined = NominationResponse.builder()
                .id(NOMINATION_ID).status(NominationStatus.DECLINED).build();
        when(service.declineNomination(NOMINATION_ID, CONTRIBUTOR_ID)).thenReturn(declined);

        mvc.perform(put("/api/v1/nominations/{id}/decline", NOMINATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ReviewNominationRequest(CONTRIBUTOR_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"));
    }

    // ── getGuesses ────────────────────────────────────────────────────────────

    @Test
    void getGuesses_returns200() throws Exception {
        when(service.getGuesses(PLAYLIST_ID)).thenReturn(
                List.of(new GuessResponse(NOMINATION_ID, CONTRIBUTOR_ID, CONTRIBUTOR_ID)));

        mvc.perform(get("/api/v1/playlists/{id}/guesses", PLAYLIST_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nominationId").value(NOMINATION_ID))
                .andExpect(jsonPath("$[0].guesserId").value(CONTRIBUTOR_ID));
    }

    // ── publishPlaylist ───────────────────────────────────────────────────────

    @Test
    void publishPlaylist_returns200() throws Exception {
        var published = new PlaylistResponse(PLAYLIST_ID, "Mix", null, PlaylistStatus.PUBLISHED,
                CONTRIBUTOR_ID, null, null, Instant.EPOCH, null, 0L, Instant.EPOCH, Instant.EPOCH);
        when(service.publishPlaylist(PLAYLIST_ID, CONTRIBUTOR_ID)).thenReturn(published);

        mvc.perform(post("/api/v1/playlists/{id}/publish", PLAYLIST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PublishPlaylistRequest(CONTRIBUTOR_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void publishPlaylist_returns409_whenDeadlineNotPassed() throws Exception {
        when(service.publishPlaylist(PLAYLIST_ID, CONTRIBUTOR_ID))
                .thenThrow(new IllegalStateException("Playlist deadline has not yet passed"));

        mvc.perform(post("/api/v1/playlists/{id}/publish", PLAYLIST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PublishPlaylistRequest(CONTRIBUTOR_ID))))
                .andExpect(status().isConflict());
    }

    @Test
    void publishPlaylist_returns400_whenBodyInvalid() throws Exception {
        mvc.perform(post("/api/v1/playlists/{id}/publish", PLAYLIST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contributorId\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
