package com.geer.snowboard.v2.bootstrap;

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
    SessionLifetimeFilter(Clock clock) { this.clock = clock; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var session = request.getSession(false);
        if (session != null && session.getAttribute("identity.authenticatedAt") instanceof Long loggedInAt
                && clock.millis() - loggedInAt >= ABSOLUTE_MILLIS) {
            session.invalidate();
            SecurityContextHolder.clearContext();
            response.setStatus(401);
            response.setContentType("application/problem+json");
            response.getWriter().write("{\"status\":401,\"title\":\"Unauthorized\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
