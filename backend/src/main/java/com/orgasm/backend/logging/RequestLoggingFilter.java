package com.orgasm.backend.logging;

import com.orgasm.backend.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Puts the per-request correlation context into the MDC for the duration of the request so every
 * log line written while handling it (controllers, backend-core services, Hibernate, ...) carries
 * it, and writes one structured "access log" line on completion with status + duration.
 *
 * <p>Sits after {@code TenantResolverFilter} in the security chain so the tenant and the JWT
 * subject are already resolved. The MDC is cleared unconditionally in {@code finally}: Tomcat
 * reuses request threads, so a leaked value would attach to the next request on that thread.
 *
 * <p>Only opaque ids go into the MDC (request id, tenant id, JWT {@code sub}). Client IP, user
 * agent, query string (the list endpoints accept a {@code name} filter) and any JWT claim that
 * identifies the person are deliberately not logged.
 */
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-ID";

    /** An inbound X-Request-ID is caller-controlled; anything outside this shape is replaced, not trusted. */
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private static final String[] MDC_KEYS = {
            LogFields.REQUEST_ID, LogFields.REQUEST_METHOD, LogFields.URL_PATH,
            LogFields.TENANT_ID, LogFields.USER_ID
    };

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        long start = System.nanoTime();

        MDC.put(LogFields.REQUEST_ID, requestId);
        MDC.put(LogFields.REQUEST_METHOD, request.getMethod());
        MDC.put(LogFields.URL_PATH, request.getRequestURI());
        Long tenantId = TenantContext.get();
        if (tenantId != null) {
            MDC.put(LogFields.TENANT_ID, tenantId.toString());
        }
        String userId = resolveUserId();
        if (userId != null) {
            MDC.put(LogFields.USER_ID, userId);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            logCompletion(request, response.getStatus(), System.nanoTime() - start);
            for (String key : MDC_KEYS) {
                MDC.remove(key);
            }
        }
    }

    static String resolveRequestId(HttpServletRequest request) {
        String supplied = request.getHeader(REQUEST_ID_HEADER);
        if (supplied != null && SAFE_REQUEST_ID.matcher(supplied).matches()) {
            return supplied;
        }
        return UUID.randomUUID().toString();
    }

    private static String resolveUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getSubject();
        }
        return null;
    }

    private static void logCompletion(HttpServletRequest request, int status, long durationNanos) {
        // Health probes and metrics scrapes would otherwise dominate the log volume.
        boolean actuator = request.getRequestURI().startsWith("/actuator");
        LoggingEventBuilder event = actuator ? log.atDebug() : log.atInfo();
        event.addKeyValue(LogFields.RESPONSE_STATUS, status)
                .addKeyValue(LogFields.EVENT_DURATION, durationNanos)
                .log("HTTP request completed");
    }
}
