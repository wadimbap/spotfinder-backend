package com.spotfinder.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.spotfinder.auth.dto.AuthResponse;
import com.spotfinder.auth.dto.RegisterRequest;
import com.spotfinder.auth.repository.RefreshSessionRepository;
import com.spotfinder.auth.repository.RefreshSessionRepository.Session;
import com.spotfinder.auth.repository.RefreshSessionRepository.Token;
import com.spotfinder.auth.security.JwtService;
import com.spotfinder.auth.security.RefreshTokenCodec;
import com.spotfinder.common.exception.InvalidRefreshTokenException;
import com.spotfinder.user.dto.UserResponse;
import com.spotfinder.user.dto.mapper.UserMapper;
import com.spotfinder.user.entity.UserEntity;
import com.spotfinder.user.entity.UserRole;
import com.spotfinder.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshSessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();
    private final RefreshTokenCodec codec = new RefreshTokenCodec(
            "test-secret-test-secret-test-secret-test-secret"
    );

    @Mock
    private AuthenticationService authentication;
    @Mock
    private RegistrationService registration;
    @Mock
    private RefreshSessionRepository repository;
    @Mock
    private JwtService jwt;
    @Mock
    private UserRepository users;
    @Mock
    private UserMapper mapper;

    private RefreshSessionService service;

    @BeforeEach
    void setUp() {
        service = new RefreshSessionService(
                authentication, registration, repository, codec, jwt, users, mapper,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "invalid", "..........................................."})
    void malformedTokensAreRejectedBeforeDatabaseAccess(String token) {
        assertThatThrownBy(() -> service.refresh(token, UUID.randomUUID()))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verifyNoInteractions(repository, jwt, users);
    }

    @Test
    void rotatesAndCapsIdleExpiryAtAbsoluteExpiry() {
        String raw = codec.generate();
        UUID requestId = UUID.randomUUID();
        Instant absoluteExpiry = NOW.plus(Duration.ofDays(10));
        Session session = session(raw, NOW.plus(Duration.ofDays(1)), absoluteExpiry);
        known(raw, session);
        when(repository.findToken(codec.hash(raw))).thenReturn(Optional.of(new Token(null, null)));
        stubUser(absoluteExpiry);

        var result = service.refresh(raw, requestId);

        assertThat(result.refreshToken()).isEqualTo(codec.successor(raw, requestId));
        assertThat(result.expiresAt()).isEqualTo(absoluteExpiry);
        assertThat(result.auth().accessToken()).isEqualTo("new-access");
        verify(repository).rotate(
                SESSION_ID, codec.hash(raw), codec.hash(result.refreshToken()),
                requestId, NOW, absoluteExpiry
        );
    }

    @Test
    void lostResponseRetryDoesNotExtendLifetimeOrRotateAgain() {
        String old = codec.generate();
        UUID requestId = UUID.randomUUID();
        String successor = codec.successor(old, requestId);
        Instant expiresAt = NOW.plus(Duration.ofDays(2));
        Session session = session(successor, expiresAt, NOW.plus(Duration.ofDays(80)));
        known(old, session);
        when(repository.findToken(codec.hash(old)))
                .thenReturn(Optional.of(new Token(requestId, NOW.minusSeconds(5))));
        stubUser(expiresAt);

        var result = service.refresh(old, requestId);

        assertThat(result.refreshToken()).isEqualTo(successor);
        assertThat(result.expiresAt()).isEqualTo(expiresAt);
        verify(repository, never()).rotate(any(), any(), any(), any(), any(), any());
    }

    @Test
    void differentRequestReusingConsumedTokenRevokesItsSession() {
        String old = codec.generate();
        UUID firstRequest = UUID.randomUUID();
        known(old, session(
                codec.successor(old, firstRequest),
                NOW.plusSeconds(100), NOW.plusSeconds(200)
        ));
        when(repository.findToken(codec.hash(old)))
                .thenReturn(Optional.of(new Token(firstRequest, NOW.minusSeconds(1))));

        assertThatThrownBy(() -> service.refresh(old, UUID.randomUUID()))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(repository).revoke(SESSION_ID, NOW);
        verifyNoInteractions(jwt, users);
    }

    @Test
    void retryWindowIsExclusiveAtSixtySeconds() {
        String old = codec.generate();
        UUID requestId = UUID.randomUUID();
        known(old, session(
                codec.successor(old, requestId), NOW.plusSeconds(100), NOW.plusSeconds(200)
        ));
        when(repository.findToken(codec.hash(old)))
                .thenReturn(Optional.of(new Token(requestId, NOW.minusSeconds(60))));

        assertThatThrownBy(() -> service.refresh(old, requestId))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(repository).revoke(SESSION_ID, NOW);
    }

    @Test
    void retryCannotRestoreAnOlderGenerationAfterSuccessorWasUsed() {
        String old = codec.generate();
        UUID requestId = UUID.randomUUID();
        known(old, session(codec.generate(), NOW.plusSeconds(100), NOW.plusSeconds(200)));
        when(repository.findToken(codec.hash(old)))
                .thenReturn(Optional.of(new Token(requestId, NOW.minusSeconds(1))));

        assertThatThrownBy(() -> service.refresh(old, requestId))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(repository).revoke(SESSION_ID, NOW);
    }

    @Test
    void idleExpiryIsExclusive() {
        String raw = codec.generate();
        known(raw, session(raw, NOW, NOW.plusSeconds(100)));

        assertThatThrownBy(() -> service.refresh(raw, UUID.randomUUID()))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(repository).revoke(SESSION_ID, NOW);
        verify(repository, never()).findToken(any());
    }

    @Test
    void absoluteExpiryIsExclusive() {
        String raw = codec.generate();
        known(raw, session(raw, NOW.plusSeconds(100), NOW));

        assertThatThrownBy(() -> service.refresh(raw, UUID.randomUUID()))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(repository).revoke(SESSION_ID, NOW);
    }

    @Test
    void randomUnknownTokenCannotRevokeASession() {
        String raw = codec.generate();
        when(repository.findSessionId(codec.hash(raw))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refresh(raw, UUID.randomUUID()))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(repository, never()).revoke(any(), any());
    }

    @Test
    void registrationFlushesUserBeforeInsertingSession() {
        RegisterRequest request = new RegisterRequest(
                "user@example.com", "User", "password123", "password123"
        );
        when(registration.register(request)).thenReturn(new AuthResponse(
                "access", "Bearer", userResponse()
        ));

        var result = service.register(request, null);

        var order = inOrder(users, repository);
        order.verify(users).flush();
        order.verify(repository).create(
                any(), eq(USER_ID), eq(codec.hash(result.refreshToken())), eq(NOW),
                eq(NOW.plus(Duration.ofDays(30))), eq(NOW.plus(Duration.ofDays(90)))
        );
    }

    private Session session(String current, Instant idle, Instant absolute) {
        return new Session(SESSION_ID, USER_ID, codec.hash(current), idle, absolute, null);
    }

    private void known(String raw, Session session) {
        when(repository.findSessionId(codec.hash(raw))).thenReturn(Optional.of(SESSION_ID));
        when(repository.lockSession(SESSION_ID)).thenReturn(Optional.of(session));
    }

    private void stubUser(Instant expiresAt) {
        UserEntity user = new UserEntity();
        user.setId(USER_ID);
        user.setEmail("user@example.com");
        user.setRole(UserRole.USER);
        when(users.findById(USER_ID)).thenReturn(Optional.of(user));
        when(jwt.generateAccessToken(USER_ID, user.getEmail(), "USER", expiresAt))
                .thenReturn("new-access");
        when(mapper.toResponse(user)).thenReturn(userResponse());
    }

    private UserResponse userResponse() {
        return new UserResponse(USER_ID, "user@example.com", "User", UserRole.USER, null, true, NOW);
    }
}
