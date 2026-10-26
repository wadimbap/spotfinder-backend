package com.spotfinder.auth.service;

import com.spotfinder.auth.dto.AuthResponse;
import com.spotfinder.auth.dto.LoginRequest;
import com.spotfinder.auth.dto.RegisterRequest;
import com.spotfinder.auth.repository.RefreshSessionRepository;
import com.spotfinder.auth.repository.RefreshSessionRepository.Session;
import com.spotfinder.auth.repository.RefreshSessionRepository.Token;
import com.spotfinder.auth.security.JwtService;
import com.spotfinder.auth.security.RefreshTokenCodec;
import com.spotfinder.common.exception.InvalidRefreshTokenException;
import com.spotfinder.user.dto.mapper.UserMapper;
import com.spotfinder.user.entity.UserEntity;
import com.spotfinder.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefreshSessionService {

    private static final Duration IDLE_TIMEOUT = Duration.ofDays(30);
    private static final Duration ABSOLUTE_TIMEOUT = Duration.ofDays(90);
    private static final Duration RETRY_WINDOW = Duration.ofSeconds(60);

    private final AuthenticationService authenticationService;
    private final RegistrationService registrationService;
    private final RefreshSessionRepository repository;
    private final RefreshTokenCodec codec;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final Clock authClock;

    public record Result(
            AuthResponse auth,
            String refreshToken,
            Instant expiresAt
    ) {
    }

    @Transactional
    public Result login(LoginRequest request, String previousRefreshToken) {
        AuthResponse auth = authenticationService.login(request);

        revokeKnownSession(previousRefreshToken);

        return create(auth);
    }

    @Transactional
    public Result register(RegisterRequest request, String previousRefreshToken) {
        AuthResponse auth = registrationService.register(request);

        // JPA must insert the user before JDBC inserts its session.
        userRepository.flush();

        revokeKnownSession(previousRefreshToken);

        return create(auth);
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Result refresh(String refreshToken, UUID requestId) {
        if (!codec.isValidFormat(refreshToken) || requestId == null) {
            throw new InvalidRefreshTokenException();
        }

        String hash = codec.hash(refreshToken);

        UUID sessionId = repository.findSessionId(hash)
                .orElseThrow(InvalidRefreshTokenException::new);

        Session session = repository.lockSession(sessionId)
                .orElseThrow(InvalidRefreshTokenException::new);

        Instant now = authClock.instant();

        if (session.revokedAt() != null) {
            throw new InvalidRefreshTokenException();
        }

        if (!session.idleExpiresAt().isAfter(now)
                || !session.absoluteExpiresAt().isAfter(now)) {
            throw revokeAndReject(session, now);
        }

        // Read token state after acquiring the session lock.
        Token token = repository.findToken(hash)
                .orElseThrow(InvalidRefreshTokenException::new);

        String nextToken = codec.successor(refreshToken, requestId);
        String nextHash = codec.hash(nextToken);

        if (token.consumedAt() != null) {
            boolean sameRequest = requestId.equals(token.requestId());
            boolean insideRetryWindow =
                    now.isBefore(token.consumedAt().plus(RETRY_WINDOW));
            boolean successorStillCurrent = nextHash.equals(session.currentHash());

            if (!sameRequest || !insideRetryWindow || !successorStillCurrent) {
                throw revokeAndReject(session, now);
            }

            // Repeating a lost response does not extend session lifetime.
            return response(session, nextToken, session.idleExpiresAt());
        }

        if (!hash.equals(session.currentHash())) {
            throw revokeAndReject(session, now);
        }

        Instant idleExpiresAt = earlier(
                now.plus(IDLE_TIMEOUT),
                session.absoluteExpiresAt()
        );

        repository.rotate(
                session.id(),
                hash,
                nextHash,
                requestId,
                now,
                idleExpiresAt
        );

        return response(session, nextToken, idleExpiresAt);
    }

    @Transactional
    public void logout(String refreshToken) {
        revokeKnownSession(refreshToken);
    }

    private Result create(AuthResponse auth) {
        Instant now = authClock.instant();
        Instant idleExpiresAt = now.plus(IDLE_TIMEOUT);
        Instant absoluteExpiresAt = now.plus(ABSOLUTE_TIMEOUT);

        String refreshToken = codec.generate();

        repository.create(
                UUID.randomUUID(),
                auth.user().id(),
                codec.hash(refreshToken),
                now,
                idleExpiresAt,
                absoluteExpiresAt
        );

        return new Result(auth, refreshToken, idleExpiresAt);
    }

    private Result response(
            Session session,
            String refreshToken,
            Instant expiresAt
    ) {
        UserEntity user = userRepository.findById(session.userId())
                .orElseThrow(InvalidRefreshTokenException::new);

        String accessToken = jwtService.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                expiresAt
        );

        AuthResponse auth = new AuthResponse(
                accessToken,
                "Bearer",
                userMapper.toResponse(user)
        );

        return new Result(auth, refreshToken, expiresAt);
    }

    private void revokeKnownSession(String refreshToken) {
        if (!codec.isValidFormat(refreshToken)) {
            return;
        }

        repository.findSessionId(codec.hash(refreshToken))
                .flatMap(repository::lockSession)
                .ifPresent(session ->
                        repository.revoke(session.id(), authClock.instant())
                );
    }

    private InvalidRefreshTokenException revokeAndReject(
            Session session,
            Instant now
    ) {
        repository.revoke(session.id(), now);
        return new InvalidRefreshTokenException();
    }

    private Instant earlier(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }
}
