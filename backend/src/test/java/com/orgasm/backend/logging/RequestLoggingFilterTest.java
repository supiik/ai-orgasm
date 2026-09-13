package com.orgasm.backend.logging;

import com.orgasm.backend.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        MDC.clear();
    }

    /** Captures the MDC as seen by whatever runs downstream of the filter. */
    private static final class CapturingChain extends MockFilterChain {
        Map<String, String> mdcDuringRequest;
        int statusToReturn = 200;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
            mdcDuringRequest = new HashMap<>(MDC.getCopyOfContextMap());
            ((MockHttpServletResponse) response).setStatus(statusToReturn);
        }
    }

    @Test
    void populatesMdcForTheRequestAndClearsItAfterwards() throws Exception {
        TenantContext.set(7L);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                Jwt.withTokenValue("t").header("alg", "none")
                        .subject("3f2c0a5e-0000-4000-8000-000000000001")
                        .claim("email", "alice@example.com")
                        .claim("preferred_username", "alice")
                        .build()));
        var request = new MockHttpServletRequest("PUT", "/api/v1/playlists/42");
        request.setQueryString("name=Alice%20Smith");
        var chain = new CapturingChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.mdcDuringRequest)
                .containsEntry(LogFields.REQUEST_METHOD, "PUT")
                .containsEntry(LogFields.URL_PATH, "/api/v1/playlists/42")
                .containsEntry(LogFields.TENANT_ID, "7")
                .containsEntry(LogFields.USER_ID, "3f2c0a5e-0000-4000-8000-000000000001")
                .containsKey(LogFields.REQUEST_ID);
        // nothing that identifies the person, and no query string (list filters can carry names)
        assertThat(chain.mdcDuringRequest.values()).noneMatch(v -> v.contains("alice") || v.contains("Alice"));
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void anonymousRequestHasNoTenantOrUser() throws Exception {
        var chain = new CapturingChain();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/organizations"), new MockHttpServletResponse(), chain);

        assertThat(chain.mdcDuringRequest)
                .doesNotContainKeys(LogFields.TENANT_ID, LogFields.USER_ID)
                .containsKey(LogFields.REQUEST_ID);
    }

    @Test
    void echoesAWellFormedInboundRequestId() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/playlists");
        request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, "gw-1234.abc_XYZ-9");
        var response = new MockHttpServletResponse();
        var chain = new CapturingChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER)).isEqualTo("gw-1234.abc_XYZ-9");
        assertThat(chain.mdcDuringRequest).containsEntry(LogFields.REQUEST_ID, "gw-1234.abc_XYZ-9");
    }

    @Test
    void replacesAnUnsafeInboundRequestId() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/playlists");
        request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, "evil\n\"injected\": true," + "x".repeat(100));
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new CapturingChain());

        String generated = response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);
        assertThat(generated).isNotNull().doesNotContain("evil").matches("[0-9a-f-]{36}");
    }

    @Test
    void clearsMdcEvenWhenDownstreamThrows() {
        var chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
                throw new IllegalStateException("boom");
            }
        };

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                filter.doFilter(new MockHttpServletRequest("GET", "/x"), new MockHttpServletResponse(), chain))
                .isInstanceOf(IllegalStateException.class);

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }
}
