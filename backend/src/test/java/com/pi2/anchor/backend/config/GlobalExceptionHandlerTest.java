package com.pi2.anchor.backend.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.accept.InvalidApiVersionException;
import org.springframework.web.accept.MissingApiVersionException;
import org.springframework.web.accept.NotAcceptableApiVersionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {

    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders
                .standaloneSetup(new StubController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void handlesMalformedJson() throws Exception {
        mvc.perform(post("/test/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void handlesIllegalState() throws Exception {
        mvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void handlesUnexpectedException() throws Exception {
        mvc.perform(get("/test/error"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500));
    }

    @Test
    void handlesMissingApiVersion() throws Exception {
        mvc.perform(get("/test/missing-version"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void handlesInvalidApiVersion() throws Exception {
        mvc.perform(get("/test/invalid-version"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void handlesNotAcceptableApiVersion() throws Exception {
        mvc.perform(get("/test/not-acceptable-version"))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @RestController
    static class StubController {

        @PostMapping("/test/body")
        void body(@RequestBody Map<String, String> ignored) {}

        @GetMapping("/test/conflict")
        void conflict() { throw new IllegalStateException("already exists"); }

        @GetMapping("/test/error")
        void error() throws Exception { throw new RuntimeException("unexpected"); }

        @GetMapping("/test/missing-version")
        void missingVersion() { throw new MissingApiVersionException(); }

        @GetMapping("/test/invalid-version")
        void invalidVersion() { throw new InvalidApiVersionException("v99"); }

        @GetMapping("/test/not-acceptable-version")
        void notAcceptableVersion() { throw new NotAcceptableApiVersionException("v0"); }
    }
}
