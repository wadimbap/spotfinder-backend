package com.spotfinder.comment.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class CommentPersistenceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @BeforeAll
    static void migrateSchema() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("filesystem:src/main/resources/db/migration")
                .load()
                .migrate();
    }

    @Test
    void migration_shouldSupportIdentityRepliesForeignKeysCascadeAndChronologicalReads() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID spotId = UUID.randomUUID();

        try (Connection connection = connection()) {
            insertUser(connection, userId);
            insertSpot(connection, spotId, userId);

            long rootId = insertComment(
                    connection,
                    userId,
                    spotId,
                    "Root",
                    null,
                    OffsetDateTime.parse("2026-08-23T10:00:00Z")
            );
            long replyId = insertComment(
                    connection,
                    userId,
                    spotId,
                    "Reply",
                    rootId,
                    OffsetDateTime.parse("2026-08-23T10:01:00Z")
            );

            assertThat(rootId).isPositive();
            assertThat(replyId).isGreaterThan(rootId);
            assertThat(commentIdsBySpotInCreatedOrder(connection, spotId))
                    .containsExactly(rootId, replyId);

            assertThatThrownBy(() -> insertComment(
                    connection,
                    userId,
                    spotId,
                    "Broken reply",
                    999_999L,
                    OffsetDateTime.parse("2026-08-23T10:02:00Z")
            )).isInstanceOf(SQLException.class);

            try (PreparedStatement statement = connection.prepareStatement("delete from spots where id = ?")) {
                statement.setObject(1, spotId);
                statement.executeUpdate();
            }

            assertThat(commentIdsBySpotInCreatedOrder(connection, spotId)).isEmpty();
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static void insertUser(Connection connection, UUID userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into users (id, email, password_hash, display_name, role, enabled, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, current_timestamp, current_timestamp)
                """)) {
            statement.setObject(1, userId);
            statement.setString(2, userId + "@example.com");
            statement.setString(3, "password-hash");
            statement.setString(4, "user");
            statement.setString(5, "USER");
            statement.setBoolean(6, true);
            statement.executeUpdate();
        }
    }

    private static void insertSpot(Connection connection, UUID spotId, UUID userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into spots (
                    id, name, description, latitude, longitude, type, created_by_user_id, approved,
                    created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, current_timestamp, current_timestamp)
                """)) {
            statement.setObject(1, spotId);
            statement.setString(2, "Central Plaza");
            statement.setString(3, "Description");
            statement.setDouble(4, 55.75);
            statement.setDouble(5, 37.62);
            statement.setString(6, "STREET");
            statement.setObject(7, userId);
            statement.setBoolean(8, true);
            statement.executeUpdate();
        }
    }

    private static long insertComment(
            Connection connection,
            UUID userId,
            UUID spotId,
            String text,
            Long parentCommentId,
            OffsetDateTime createdAt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into comments (created_by, spot_id, text, parent_comment_id, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?)
                returning id
                """)) {
            statement.setObject(1, userId);
            statement.setObject(2, spotId);
            statement.setString(3, text);
            statement.setObject(4, parentCommentId);
            statement.setObject(5, createdAt);
            statement.setObject(6, createdAt);

            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong("id");
            }
        }
    }

    private static List<Long> commentIdsBySpotInCreatedOrder(Connection connection, UUID spotId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                select id from comments where spot_id = ? order by created_at asc
                """)) {
            statement.setObject(1, spotId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<Long> ids = new ArrayList<>();
                while (resultSet.next()) {
                    ids.add(resultSet.getLong("id"));
                }
                return ids;
            }
        }
    }
}
