package com.spotfinder.auth.security;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.spotfinder.user.entity.UserRole;
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
}
