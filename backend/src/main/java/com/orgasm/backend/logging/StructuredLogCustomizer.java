package com.orgasm.backend.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

/**
 * Adds to Spring Boot's built-in ECS JSON formatter what it doesn't emit on its own:
 * <ul>
 *   <li>{@code process.thread.id} — Boot only writes {@code process.thread.name}. Read from the
 *       current thread, which is the logging thread because the console appender is synchronous
 *       (do not put an {@code AsyncAppender} in front of this encoder). Emitted as a dotted key,
 *       see the comment in {@link #customize}.</li>
 *   <li>{@code trace.id} / {@code span.id} — Micrometer Tracing puts them in the MDC as
 *       {@code traceId}/{@code spanId}; ECS wants the nested names, which is what Elastic APM /
 *       Kibana correlate logs to traces on. The raw MDC keys are dropped via
 *       {@code logging.structured.json.exclude} in application.yml.</li>
 *   <li>PII scrubbing of every string value via {@link PiiMasker}.</li>
 * </ul>
 * Registered through {@code logging.structured.json.customizer} (application.yml), which
 * instantiates it reflectively — keep the no-arg constructor.
 */
public class StructuredLogCustomizer implements StructuredLoggingJsonMembersCustomizer<ILoggingEvent> {

    @Override
    public void customize(JsonWriter.Members<ILoggingEvent> members) {
        // Boot already writes the nested `process` object and JsonWriter rejects a second member
        // with the same name, so this one is a dotted top-level key — Elasticsearch/ECS treat
        // "process.thread.id" and {"process":{"thread":{"id":..}}} as the same field.
        members.add("process.thread.id", event -> Thread.currentThread().threadId());

        members.add("trace").whenNotNull(event -> mdc(event, LogFields.MDC_TRACE_ID))
                .usingMembers(trace -> trace.add("id", event -> mdc(event, LogFields.MDC_TRACE_ID)));
        members.add("span").whenNotNull(event -> mdc(event, LogFields.MDC_SPAN_ID))
                .usingMembers(span -> span.add("id", event -> mdc(event, LogFields.MDC_SPAN_ID)));

        members.applyingValueProcessor(JsonWriter.ValueProcessor.of(String.class, PiiMasker::mask));
    }

    private static String mdc(ILoggingEvent event, String key) {
        return event.getMDCPropertyMap().get(key);
    }
}
