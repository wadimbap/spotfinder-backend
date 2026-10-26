package com.spotfinder.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

public class AuthCsrfGuard extends OncePerRequestFilter {

    private final List<String> allowedOrigins;

    public AuthCsrfGuard(List<String> allowedOrigins) {
        this.allowedOrigins = List.copyOf(allowedOrigins);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) {
            return true;
        }

        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);

        return !path.equals("/api/v1/auth/login")
                && !path.equals("/api/v1/auth/register")
                && !path.equals("/api/v1/auth/refresh")
                && !path.equals("/api/v1/auth/logout");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);

        boolean cookieEndpoint = path.equals("/api/v1/auth/refresh")
                || path.equals("/api/v1/auth/logout");

        boolean untrustedOrigin = origin != null && !allowedOrigins.contains(origin);

        boolean missingCookieProtection = cookieEndpoint
                && (origin == null || !"1".equals(request.getHeader("X-Auth-Request")));

        if (untrustedOrigin || missingCookieProtection) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("""
                    {"code":"AUTH_CSRF_REJECTED","message":"Untrusted auth request","fieldErrors":{}}
                    """);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
