package com.orgasm.backend.filter;

import com.orgasm.backend.tenant.TenantContext;
import com.orgasm.backend.tenant.TenantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(1)
@RequiredArgsConstructor
public class TenantResolverFilter extends OncePerRequestFilter {

    private final TenantRepository tenantRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String slug = request.getHeader("X-Tenant-ID");
        if (slug == null || slug.isBlank()) {
            slug = "default";
        }
        var tenant = tenantRepository.findBySlug(slug);
        if (tenant.isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown tenant: " + slug);
            return;
        }
        TenantContext.set(tenant.get().getId());
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
