package com.geer.snowboard.v2.bootstrap;

import com.geer.snowboard.v2.identity.application.port.in.IdentityOperations;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

final class SessionLifetimeFilter extends OncePerRequestFilter {
    private static final long ABSOLUTE_MILLIS = 12L * 60 * 60 * 1000;
    private final Clock clock;
    private final IdentityOperations identity;
    SessionLifetimeFilter(Clock clock, IdentityOperations identity) {
        this.clock = clock;
        this.identity = identity;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var session = request.getSession(false);
        if (session != null && session.getAttribute("identity.authenticatedAt") instanceof Long loggedInAt) {
            Object accountId = session.getAttribute("identity.accountId");
            Object version = session.getAttribute("identity.credentialVersion");
            Long current = accountId instanceof String id ? identity.credentialVersion(id) : null;
            if (clock.millis() - loggedInAt >= ABSOLUTE_MILLIS
                    || !(version instanceof Long saved) || current == null || current.longValue() != saved.longValue()) {
                session.invalidate();
                SecurityContextHolder.clearContext();
                if (request.getRequestURI().equals("/api/auth/csrf")) {
                    chain.doFilter(request, response);
                    return;
                }
                response.setStatus(401);
                response.setContentType("application/problem+json");
                response.getWriter().write("{\"status\":401,\"title\":\"Unauthorized\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
