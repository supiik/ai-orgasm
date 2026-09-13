package com.orgasm.backend.logging;

/**
 * MDC / structured-log field names, following the Elastic Common Schema (ECS) naming so the JSON
 * output is ingestible by Elastic, Datadog, Loki, OpenSearch etc. without a field-mapping layer.
 * Dotted names are emitted as nested JSON objects by Spring Boot's ECS formatter
 * (e.g. {@code http.request.id} → {@code {"http":{"request":{"id":...}}}}).
 *
 * <p>Deliberately no field for email, name, username, client IP or user agent — those are PII and
 * must never be put in the MDC. Only opaque identifiers are logged; see {@link PiiMasker} for the
 * safety net that scrubs anything that slips into a message.
 */
public final class LogFields {

    /** Correlation id for one HTTP request; taken from the {@code X-Request-ID} header or generated. */
    public static final String REQUEST_ID = "http.request.id";
    public static final String REQUEST_METHOD = "http.request.method";
    public static final String URL_PATH = "url.path";
    public static final String RESPONSE_STATUS = "http.response.status_code";
    /** Wall-clock duration of the request in nanoseconds (ECS: {@code event.duration} is nanos). */
    public static final String EVENT_DURATION = "event.duration";
    /** Tenant the request runs under (the {@code tenant_id} JWT claim / {@code Organization.id}). */
    public static final String TENANT_ID = "tenant.id";
    /** Opaque identity-provider subject ({@code sub} claim) — never the email or username. */
    public static final String USER_ID = "user.id";

    /** MDC keys written by Micrometer Tracing; renamed to ECS {@code trace.id}/{@code span.id} on output. */
    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_SPAN_ID = "spanId";

    private LogFields() {}
}
