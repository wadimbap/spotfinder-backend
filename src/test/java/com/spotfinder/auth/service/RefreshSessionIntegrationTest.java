package com.spotfinder.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.spotfinder.auth.dto.LoginRequest;
import com.spotfinder.auth.dto.RegisterRequest;
import com.spotfinder.auth.security.JwtService;
import com.spotfinder.common.exception.InvalidRefreshTokenException;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "storage.minio.initialize-bucket=false",
        "app.jwt.expiration-minutes=15",
        "app.auth.cookie-secure=true"
})
@Import(RefreshSessionIntegrationTest.ClockConfiguration.class)
class RefreshSessionIntegrationTest {

    private static final String ORIGIN = "http://localhost:5173";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private RefreshSessionService sessions;
    @Autowired
    private JwtService jwt;
    @Autowired
    private MutableClock clock;
    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetClock() {
        clock.set(Instant.now().truncatedTo(ChronoUnit.MICROS));
    }

    @Test
    void sameRequestCanRecoverLostResponse() {
        var initial = register();
        UUID requestId = UUID.randomUUID();
        var first = sessions.refresh(initial.refreshToken(), requestId);
        var retry = sessions.refresh(initial.refreshToken(), requestId);

        assertThat(retry.refreshToken()).isEqualTo(first.refreshToken());
        assertThat(retry.expiresAt()).isEqualTo(first.expiresAt());
    }

    @Test
    void replayRevocationIsCommittedDespiteTheException() {
        var initial = register();
        var rotated = sessions.refresh(initial.refreshToken(), UUID.randomUUID());

        assertThatThrownBy(() -> sessions.refresh(initial.refreshToken(), UUID.randomUUID()))
                .isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> sessions.refresh(rotated.refreshToken(), UUID.randomUUID()))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void idleSessionExpiresAtThirtyDays() {
        var initial = register();
        clock.set(clock.instant().plus(Duration.ofDays(30)));

        assertThatThrownBy(() -> sessions.refresh(initial.refreshToken(), UUID.randomUUID()))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void activeSessionStillExpiresAtNinetyDays() {
        Instant startedAt = clock.instant();
        var current = register();

        for (int day : new int[]{29, 58, 87}) {
            clock.set(startedAt.plus(Duration.ofDays(day)));
            current = sessions.refresh(current.refreshToken(), UUID.randomUUID());
        }

        assertThat(current.expiresAt()).isEqualTo(startedAt.plus(Duration.ofDays(90)));
        String token = current.refreshToken();
        clock.set(startedAt.plus(Duration.ofDays(90)));

        assertThatThrownBy(() -> sessions.refresh(token, UUID.randomUUID()))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void logoutLeavesAnotherDeviceAndExistingAccessTokenValid() {
        String email = UUID.randomUUID() + "@example.com";
        var first = sessions.register(request(email), null);
        var second = sessions.login(new LoginRequest(email, "password123"), null);

        sessions.logout(first.refreshToken());

        assertThatThrownBy(() -> sessions.refresh(first.refreshToken(), UUID.randomUUID()))
                .isInstanceOf(InvalidRefreshTokenException.class);
        assertThat(sessions.refresh(second.refreshToken(), UUID.randomUUID()).auth().accessToken())
                .isNotBlank();
        assertThat(jwt.parseAccessToken(first.auth().accessToken()).id())
                .isEqualTo(first.auth().user().id());
    }

    @Test
    void concurrentIdenticalRequestsReturnSameSuccessor() throws Exception {
        var initial = register();
        UUID requestId = UUID.randomUUID();
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                return sessions.refresh(initial.refreshToken(), requestId);
            });
            var second = executor.submit(() -> {
                start.await();
                return sessions.refresh(initial.refreshToken(), requestId);
            });
            start.countDown();

            assertThat(first.get(15, TimeUnit.SECONDS).refreshToken())
                    .isEqualTo(second.get(15, TimeUnit.SECONDS).refreshToken());
        }
    }

    @Test
    void csrfAndCorsAllowOnlyExplicitCookieRequests() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh").header("Origin", ORIGIN))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/logout").header("X-Auth-Request", "1"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/refresh")
                        .header("Origin", "https://untrusted.example")
                        .header("X-Auth-Request", "1"))
                .andExpect(status().isForbidden());
        mvc.perform(options("/api/v1/auth/refresh")
                        .header("Origin", ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "X-Auth-Request,X-Refresh-Request"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void refreshRequiresCookieAndRequestIdAndDoesNotExposeRefreshInJson() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh")
                        .header("Origin", ORIGIN).header("X-Auth-Request", "1")
                        .header("X-Refresh-Request", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_INVALID"));
        mvc.perform(post("/api/v1/auth/refresh")
                        .header("Origin", ORIGIN).header("X-Auth-Request", "1"))
                .andExpect(status().isBadRequest());

        var initial = register();
        var response = mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("spotfinder_refresh", initial.refreshToken()))
                        .header("Origin", ORIGIN).header("X-Auth-Request", "1")
                        .header("X-Refresh-Request", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn().getResponse();

        assertThat(response.getHeader("Set-Cookie"))
                .contains("HttpOnly", "Secure", "SameSite=Lax", "Path=/api/v1/auth")
                .doesNotContain("Domain=");
    }

    @Test
    void logoutIsIdempotentAndExpiresTheCookie() throws Exception {
        var initial = register();
        for (int attempt = 0; attempt < 2; attempt++) {
            var response = mvc.perform(post("/api/v1/auth/logout")
                            .cookie(new Cookie("spotfinder_refresh", initial.refreshToken()))
                            .header("Origin", ORIGIN).header("X-Auth-Request", "1"))
                    .andExpect(status().isNoContent()).andReturn().getResponse();
            assertThat(response.getHeader("Set-Cookie")).contains("Max-Age=0");
        }
        assertThatThrownBy(() -> sessions.refresh(initial.refreshToken(), UUID.randomUUID()))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void upgradePreservesUsersAndPreviouslyAppliedMigrations() {
        String schema = "auth_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema).defaultSchema(schema).target("20261026.01").load().migrate();

        UUID user = UUID.randomUUID();
        jdbc.update("insert into " + schema + ".users "
                        + "(id, email, password_hash, display_name, role, enabled, created_at, updated_at) "
                        + "values (?, ?, ?, ?, 'USER', true, now(), now())",
                user, "preserved@example.com", "preserved-password-hash", "Preserved user");
        var historyBefore = jdbc.queryForList(
                "select version, checksum from " + schema + ".flyway_schema_history where success = true"
        );

        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema).defaultSchema(schema).load().migrate();

        assertThat(jdbc.queryForObject(
                "select password_hash from " + schema + ".users where id = ?", String.class, user
        )).isEqualTo("preserved-password-hash");
        assertThat(jdbc.queryForObject(
                "select count(*) from " + schema + ".auth_sessions", Integer.class
        )).isZero();
        assertThat(jdbc.queryForList(
                "select version, checksum from " + schema + ".flyway_schema_history where success = true"
        )).containsAll(historyBefore);
    }

    private RegisterRequest request(String email) {
        return new RegisterRequest(email, "User", "password123", "password123");
    }

    private RefreshSessionService.Result register() {
        return sessions.register(request(UUID.randomUUID() + "@example.com"), null);
    }

    @TestConfiguration
    static class ClockConfiguration {
        @Bean
        @Primary
        MutableClock testAuthClock() {
            return new MutableClock();
        }
    }

    static class MutableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.now());

        void set(Instant instant) {
            now.set(instant);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant(), zone);
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }
}
