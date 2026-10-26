package com.spotfinder.auth.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RefreshSessionRepository {

    private final JdbcTemplate jdbc;

    public record Session(
            UUID id,
            UUID userId,
            String currentHash,
            Instant idleExpiresAt,
            Instant absoluteExpiresAt,
            Instant revokedAt
    ) {
    }

    public record Token(
            UUID requestId,
            Instant consumedAt
    ) {
    }

    public void create(
            UUID id,
            UUID userId,
            String tokenHash,
            Instant now,
            Instant idleExpiresAt,
            Instant absoluteExpiresAt
    ) {
        jdbc.update("""
                insert into auth_sessions (
                    id, user_id, current_token_hash, created_at,
                    idle_expires_at, absolute_expires_at
                )
                values (?, ?, ?, ?, ?, ?)
                """,
                id,
                userId,
                tokenHash,
                Timestamp.from(now),
                Timestamp.from(idleExpiresAt),
                Timestamp.from(absoluteExpiresAt)
        );

        jdbc.update("""
                insert into auth_refresh_tokens (token_hash, session_id)
                values (?, ?)
                """, tokenHash, id);
    }

    public Optional<UUID> findSessionId(String tokenHash) {
        return jdbc.query("""
                select session_id
                from auth_refresh_tokens
                where token_hash = ?
                """,
                (rs, rowNum) -> rs.getObject("session_id", UUID.class),
                tokenHash
        ).stream().findFirst();
    }

    public Optional<Session> lockSession(UUID sessionId) {
        return jdbc.query("""
                select id, user_id, current_token_hash,
                       idle_expires_at, absolute_expires_at, revoked_at
                from auth_sessions
                where id = ?
                for update
                """,
                (rs, rowNum) -> {
                    Timestamp revokedAt = rs.getTimestamp("revoked_at");

                    return new Session(
                            rs.getObject("id", UUID.class),
                            rs.getObject("user_id", UUID.class),
                            rs.getString("current_token_hash"),
                            rs.getTimestamp("idle_expires_at").toInstant(),
                            rs.getTimestamp("absolute_expires_at").toInstant(),
                            revokedAt == null ? null : revokedAt.toInstant()
                    );
                },
                sessionId
        ).stream().findFirst();
    }

    public Optional<Token> findToken(String tokenHash) {
        return jdbc.query("""
                select request_id, consumed_at
                from auth_refresh_tokens
                where token_hash = ?
                """,
                (rs, rowNum) -> {
                    Timestamp consumedAt = rs.getTimestamp("consumed_at");

                    return new Token(
                            rs.getObject("request_id", UUID.class),
                            consumedAt == null ? null : consumedAt.toInstant()
                    );
                },
                tokenHash
        ).stream().findFirst();
    }

    public void rotate(
            UUID sessionId,
            String oldHash,
            String nextHash,
            UUID requestId,
            Instant now,
            Instant idleExpiresAt
    ) {
        int consumed = jdbc.update("""
                update auth_refresh_tokens
                set request_id = ?, consumed_at = ?
                where token_hash = ? and consumed_at is null
                """, requestId, Timestamp.from(now), oldHash);

        if (consumed != 1) {
            throw new IllegalStateException("Refresh token was concurrently consumed");
        }

        jdbc.update("""
                insert into auth_refresh_tokens (token_hash, session_id)
                values (?, ?)
                """, nextHash, sessionId);

        jdbc.update("""
                update auth_sessions
                set current_token_hash = ?, idle_expires_at = ?
                where id = ?
                """, nextHash, Timestamp.from(idleExpiresAt), sessionId);
    }

    public void revoke(UUID sessionId, Instant now) {
        jdbc.update("""
                update auth_sessions
                set revoked_at = ?
                where id = ? and revoked_at is null
                """, Timestamp.from(now), sessionId);
    }
}
