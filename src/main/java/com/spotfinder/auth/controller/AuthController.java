package com.spotfinder.auth.controller;

import com.spotfinder.auth.dto.AuthResponse;
import com.spotfinder.auth.dto.LoginRequest;
import com.spotfinder.auth.dto.RegisterRequest;
import com.spotfinder.auth.service.RefreshSessionService;
import com.spotfinder.auth.service.RefreshSessionService.Result;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_COOKIE = "spotfinder_refresh";

    private final RefreshSessionService sessions;

    @Value("${app.auth.cookie-secure:true}")
    private boolean cookieSecure;

    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            @CookieValue(name = REFRESH_COOKIE, required = false) String previousToken
    ) {
        return authenticated(
                sessions.register(request, previousToken),
                HttpStatus.CREATED
        );
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            @CookieValue(name = REFRESH_COOKIE, required = false) String previousToken
    ) {
        return authenticated(
                sessions.login(request, previousToken),
                HttpStatus.OK
        );
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            @RequestHeader("X-Refresh-Request") UUID requestId
    ) {
        return authenticated(
                sessions.refresh(refreshToken, requestId),
                HttpStatus.OK
        );
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken
    ) {
        sessions.logout(refreshToken);

        return ResponseEntity.noContent()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
                .build();
    }

    private ResponseEntity<AuthResponse> authenticated(
            Result result,
            HttpStatus status
    ) {
        long seconds = Math.max(
                0,
                Duration.between(Instant.now(), result.expiresAt()).getSeconds()
        );

        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .header(
                        HttpHeaders.SET_COOKIE,
                        cookie(result.refreshToken(), Duration.ofSeconds(seconds)).toString()
                )
                .body(result.auth());
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(maxAge)
                .build();
    }
}
