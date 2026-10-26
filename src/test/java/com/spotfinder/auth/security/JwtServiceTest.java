package com.spotfinder.auth.security;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.spotfinder.user.entity.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET =
            "test-secret-test-secret-test-secret-test-secret";

    JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 60);
    }

    @Test
    void generateAccessToken_shouldGenerateParseableToken() {
        UUID userId = UUID.randomUUID();

        String token = jwtService.generateAccessToken(
                userId,
                "user@example.com",
                UserRole.USER.name()
        );

        assertThat(token).isNotBlank();

        UserPrincipal principal = jwtService.parseAccessToken(token);

        assertThat(principal.id()).isEqualTo(userId);
        assertThat(principal.email()).isEqualTo("user@example.com");
        assertThat(principal.role()).isEqualTo(UserRole.USER);
    }

    @Test
    void accessExpiryCannotExceedTheSessionDeadline() {
        Instant deadline = Instant.now().plusSeconds(30).truncatedTo(ChronoUnit.SECONDS);
        String token = jwtService.generateAccessToken(
                UUID.randomUUID(), "user@example.com", UserRole.USER.name(), deadline
        );

        assertThat(claims(token).getExpiration().toInstant()).isEqualTo(deadline);
    }

    @Test
    void newAccessTokensHaveFifteenMinuteLifetime() {
        JwtService service = new JwtService(SECRET, 15);
        String token = service.generateAccessToken(UUID.randomUUID(), "user@example.com", "USER");
        Claims claims = claims(token);

        assertThat(claims.getExpiration().toInstant().getEpochSecond()
                - claims.getIssuedAt().toInstant().getEpochSecond()).isEqualTo(900);
    }

    @Test
    void shorterConfigurationStillAcceptsPreviouslyIssuedTokens() {
        UUID user = UUID.randomUUID();
        String legacy = new JwtService(SECRET, 60)
                .generateAccessToken(user, "user@example.com", "USER");

        assertThat(new JwtService(SECRET, 15).parseAccessToken(legacy).id()).isEqualTo(user);
    }

    private Claims claims(String token) {
        return Jwts.parser().verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build().parseSignedClaims(token).getPayload();
    }
}
