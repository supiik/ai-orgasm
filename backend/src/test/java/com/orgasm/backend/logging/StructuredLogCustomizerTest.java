package com.orgasm.backend.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives Spring Boot's real ECS encoder with the same properties application.yml sets, so this
 * pins the actual wire format other systems will ingest — not just the customizer in isolation.
 */
class StructuredLogCustomizerTest {

    private final ObjectMapper json = new ObjectMapper();
    private final LoggerContext loggerContext = new LoggerContext();
    private final StructuredLogEncoder encoder = new StructuredLogEncoder();

    @BeforeEach
    void setUp() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.application.name", "orgasm-backend")
                .withProperty("logging.structured.ecs.service.environment", "test")
                .withProperty("logging.structured.json.exclude", "traceId,spanId")
                .withProperty("logging.structured.json.customizer", StructuredLogCustomizer.class.getName());
        loggerContext.putObject(Environment.class.getName(), env);
        encoder.setContext(loggerContext);
        encoder.setFormat("ecs");
        encoder.start();
    }

    @AfterEach
    void tearDown() {
        encoder.stop();
        loggerContext.stop();
    }

    private JsonNode encode(LoggingEvent event) throws Exception {
        return json.readTree(new String(encoder.encode(event), StandardCharsets.UTF_8));
    }

    private LoggingEvent event(String message, Map<String, String> mdc) {
        var logger = loggerContext.getLogger("com.orgasm.Test");
        var event = new LoggingEvent(LoggerFactory.class.getName(), logger, Level.INFO, message, null, null);
        event.setMDCPropertyMap(mdc);
        return event;
    }

    @Test
    void emitsEcsShapeWithNestedMdcTraceAndThreadId() throws Exception {
        JsonNode line = encode(event("HTTP request completed", Map.of(
                LogFields.MDC_TRACE_ID, "4bf92f3577b34da6a3ce929d0e0e4736",
                LogFields.MDC_SPAN_ID, "00f067aa0ba902b7",
                LogFields.REQUEST_ID, "req-1",
                LogFields.REQUEST_METHOD, "GET",
                LogFields.URL_PATH, "/api/v1/playlists",
                LogFields.TENANT_ID, "7")));

        assertThat(line.get("@timestamp")).isNotNull();
        assertThat(line.at("/log/level").asText()).isEqualTo("INFO");
        assertThat(line.at("/log/logger").asText()).isEqualTo("com.orgasm.Test");
        assertThat(line.get("message").asText()).isEqualTo("HTTP request completed");
        assertThat(line.at("/service/name").asText()).isEqualTo("orgasm-backend");
        assertThat(line.at("/service/environment").asText()).isEqualTo("test");
        assertThat(line.at("/ecs/version").asText()).isNotEmpty();

        assertThat(line.at("/process/thread/name").asText()).isEqualTo(Thread.currentThread().getName());
        assertThat(line.get("process.thread.id").asLong()).isEqualTo(Thread.currentThread().threadId());

        assertThat(line.at("/trace/id").asText()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(line.at("/span/id").asText()).isEqualTo("00f067aa0ba902b7");
        assertThat(line.has("traceId")).as("raw Micrometer MDC key must not leak").isFalse();
        assertThat(line.has("spanId")).isFalse();

        assertThat(line.at("/http/request/id").asText()).isEqualTo("req-1");
        assertThat(line.at("/http/request/method").asText()).isEqualTo("GET");
        assertThat(line.at("/url/path").asText()).isEqualTo("/api/v1/playlists");
        assertThat(line.at("/tenant/id").asText()).isEqualTo("7");
    }

    @Test
    void omitsTraceAndSpanWhenNotTracing() throws Exception {
        JsonNode line = encode(event("startup", Map.of()));

        assertThat(line.has("trace")).isFalse();
        assertThat(line.has("span")).isFalse();
    }

    @Test
    void includesFluentKeyValuePairs() throws Exception {
        LoggingEvent event = event("HTTP request completed", Map.of());
        event.addKeyValuePair(new KeyValuePair(LogFields.RESPONSE_STATUS, 200));
        event.addKeyValuePair(new KeyValuePair(LogFields.EVENT_DURATION, 1234567L));

        JsonNode line = encode(event);

        assertThat(line.at("/http/response/status_code").asInt()).isEqualTo(200);
        assertThat(line.at("/event/duration").asLong()).isEqualTo(1234567L);
    }

    @Test
    void masksEmailsEverywhereInTheLine() throws Exception {
        LoggingEvent event = event("Failed to send to alice@example.com", Map.of("user.id", "bob@example.com"));
        event.addKeyValuePair(new KeyValuePair("recipient", "carol@example.com"));
        event.setThrowableProxy(new ThrowableProxy(new IllegalStateException("duplicate key dave@example.com")));

        String raw = new String(encoder.encode(event), StandardCharsets.UTF_8);
        JsonNode line = json.readTree(raw);

        assertThat(raw).doesNotContain("example.com");
        assertThat(line.get("message").asText()).isEqualTo("Failed to send to " + PiiMasker.EMAIL_PLACEHOLDER);
        assertThat(line.at("/user/id").asText()).isEqualTo(PiiMasker.EMAIL_PLACEHOLDER);
        assertThat(line.at("/error/message").asText()).contains(PiiMasker.EMAIL_PLACEHOLDER);
        assertThat(line.at("/error/type").asText()).isEqualTo(IllegalStateException.class.getName());
        assertThat(line.at("/error/stack_trace").asText()).contains(PiiMasker.EMAIL_PLACEHOLDER);
    }
}
