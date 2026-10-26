package com.spotfinder.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthCsrfGuardTest {

    private final AuthCsrfGuard guard = new AuthCsrfGuard(List.of("https://app.example.ru"));

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/auth/refresh", "/api/v1/auth/logout", "/api/v1/auth/refresh;param=1"})
    void uriMatchingDoesNotDependOnServletPath(String uri) throws Exception {
        var request = new MockHttpServletRequest("POST", uri);
        request.addHeader("Origin", "https://app.example.ru");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        guard.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void contextPathDoesNotBypassProtection() throws Exception {
        var request = new MockHttpServletRequest("POST", "/spotfinder/api/v1/auth/refresh");
        request.setContextPath("/spotfinder");
        request.addHeader("Origin", "https://app.example.ru");
        var response = new MockHttpServletResponse();

        guard.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void trustedCookieRequestReachesTheController() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/refresh");
        request.addHeader("Origin", "https://app.example.ru");
        request.addHeader("X-Auth-Request", "1");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        guard.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }
}
