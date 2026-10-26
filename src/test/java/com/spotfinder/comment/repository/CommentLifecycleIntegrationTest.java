package com.spotfinder.comment.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.spotfinder.auth.security.JwtService;
import com.spotfinder.comment.dto.CommentResponse;
import com.spotfinder.comment.dto.CreateCommentRequest;
import com.spotfinder.comment.dto.UpdateCommentRequest;
import com.spotfinder.comment.service.CommentService;
import com.spotfinder.common.exception.CommentDeletedException;
import com.spotfinder.common.exception.CommentNotFoundException;
import com.spotfinder.common.exception.CommentOwnershipException;
import com.spotfinder.spot.repository.SpotRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = "storage.minio.initialize-bucket=false")
class CommentLifecycleIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    CommentService comments;
    @Autowired
    CommentRepository repository;
    @Autowired
    SpotRepository spots;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PlatformTransactionManager transactionManager;
    @Autowired
    MockMvc mvc;
    @Autowired
    JwtService jwt;

    UUID owner;
    UUID other;
    UUID moderator;
    UUID admin;
    UUID spotId;

    @BeforeEach
    void fixtures() {
        owner = insertUser("USER");
        other = insertUser("USER");
        moderator = insertUser("MODERATOR");
        admin = insertUser("ADMIN");
        spotId = UUID.randomUUID();
        jdbc.update("""
                insert into spots (id, name, latitude, longitude, type, approved, created_by_user_id,
                                   created_at, updated_at)
                values (?, 'Comment lifecycle', 55.75, 37.62, 'STREET', true, ?, now(), now())
                """, spotId, owner);
    }

    @Test
    void deletingAnAncestor_shouldEraseTextAndPruneOnlyEmptySubtrees() {
        CommentResponse root = create(owner, null, "Root");
        CommentResponse branch = create(owner, root.id(), "Branch");
        CommentResponse child = create(other, branch.id(), "Live answer");
        CommentResponse sibling = create(other, root.id(), "Live sibling");
        CommentResponse otherRoot = create(other, null, "Another discussion");

        comments.deleteComment(owner, branch.id());

        CommentResponse tombstone = byId(branch.id());
        assertThat(tombstone.text()).isEqualTo("Комментарий удалён");
        assertThat(tombstone.deletedAt()).isNotNull();
        assertThat(tombstone.createdAt()).isEqualTo(branch.createdAt());
        assertThat(tombstone.createdByUserId()).isEqualTo(owner);
        assertThat(jdbc.queryForObject("select text from comments where id = ?", String.class, branch.id()))
                .isEmpty();
        assertThatThrownBy(() -> comments.createComment(owner,
                new CreateCommentRequest(branch.id(), "Forbidden reply"), spotId))
                .isInstanceOf(CommentDeletedException.class);
        assertThatThrownBy(() -> comments.updateComment(owner, new UpdateCommentRequest(branch.id(), "Restore")))
                .isInstanceOf(CommentDeletedException.class);
        CommentResponse grandchild = create(owner, child.id(), "Reply to the live answer");
        comments.deleteComment(owner, grandchild.id());

        comments.deleteComment(moderator, child.id());

        assertThat(ids()).containsExactly(root.id(), sibling.id(), otherRoot.id());
        assertThat(repository.existsById(branch.id())).isFalse();
        comments.deleteComment(owner, root.id());
        assertThat(byId(root.id()).deletedAt()).isNotNull();
        comments.deleteComment(admin, sibling.id());
        assertThat(ids()).containsExactly(otherRoot.id());
    }

    @Test
    void editing_shouldTrackOnlyActualTextChanges() {
        CommentResponse root = create(owner, null, "Original");
        assertThat(root.editedAt()).isNull();
        comments.updateComment(owner, new UpdateCommentRequest(root.id(), "Original"));
        assertThat(byId(root.id()).editedAt()).isNull();

        comments.updateComment(owner, new UpdateCommentRequest(root.id(), "Changed"));
        Instant editedAt = byId(root.id()).editedAt();
        assertThat(editedAt).isNotNull();
        assertThat(byId(root.id()).text()).isEqualTo("Changed");
        comments.updateComment(owner, new UpdateCommentRequest(root.id(), "Changed"));
        assertThat(byId(root.id()).editedAt()).isEqualTo(editedAt);
        assertThatThrownBy(() -> comments.updateComment(admin, new UpdateCommentRequest(root.id(), "Admin edit")))
                .isInstanceOf(CommentOwnershipException.class);
    }

    @Test
    void api_shouldEnforceAuthenticationOwnershipAndTombstoneContract() throws Exception {
        CommentResponse root = create(owner, null, "Private text");
        create(other, root.id(), "Live reply");

        mvc.perform(delete("/api/v1/comment/{id}", root.id())).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/comment/{id}", root.id()).header("Authorization", bearer(other, "USER")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/comment/{id}", root.id())
                        .header("Authorization", bearer(moderator, "MODERATOR")))
                .andExpect(status().isNoContent());
        Instant deletedAt = byId(root.id()).deletedAt();
        mvc.perform(delete("/api/v1/comment/{id}", root.id()).header("Authorization", bearer(owner, "USER")))
                .andExpect(status().isNoContent());
        assertThat(byId(root.id()).deletedAt()).isEqualTo(deletedAt);
        mvc.perform(get("/api/v1/comment").param("spotId", spotId.toString())
                        .header("Authorization", bearer(other, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].text").value("Комментарий удалён"))
                .andExpect(jsonPath("$[0].deletedAt").isNotEmpty())
                .andExpect(jsonPath("$[0].createdByUserId").value(owner.toString()));
    }

    @Test
    void deletedLeaf_shouldReturnNotFoundAfterPruning() {
        CommentResponse root = create(owner, null, "Leaf");
        comments.deleteComment(owner, root.id());

        assertThat(ids()).isEmpty();
        assertThatThrownBy(() -> comments.deleteComment(owner, root.id()))
                .isInstanceOf(CommentNotFoundException.class);
    }

    @Test
    void pendingSpot_shouldRemainHiddenFromRegularUsersIncludingCommentAuthors() throws Exception {
        CommentResponse root = create(other, null, "A comment on a public spot");
        jdbc.update("update spots set approved = false where id = ?", spotId);

        mvc.perform(delete("/api/v1/comment/{id}", root.id()).header("Authorization", bearer(other, "USER")))
                .andExpect(status().isNotFound());
        assertThat(repository.existsById(root.id())).isTrue();
        comments.deleteComment(moderator, root.id());
        assertThat(repository.existsById(root.id())).isFalse();
    }

    @Test
    void deletionBeforeReply_shouldSerializeAndRejectTheMissingParent() throws Exception {
        CommentResponse root = create(owner, null, "Root");

        runBlockedPair(() -> {
            comments.deleteComment(owner, root.id());
            return null;
        }, () -> {
            assertThatThrownBy(() -> create(other, root.id(), "Late reply"))
                    .isInstanceOf(CommentNotFoundException.class);
            return null;
        }, true);

        assertThat(ids()).isEmpty();
    }

    @Test
    void replyBeforeDeletion_shouldKeepTheNewReplyAndItsParentTombstone() throws Exception {
        CommentResponse root = create(owner, null, "Root");

        runBlockedPair(() -> create(other, root.id(), "Concurrent reply"), () -> {
            comments.deleteComment(owner, root.id());
            return null;
        });

        assertThat(ids()).hasSize(2);
        assertThat(byId(root.id()).deletedAt()).isNotNull();
        assertThat(comments.getCommentsBySpotId(owner, spotId).get(1).text()).isEqualTo("Concurrent reply");
    }

    @Test
    void concurrentSiblingDeletions_shouldPruneTheLastDeletedAncestor() throws Exception {
        CommentResponse root = create(owner, null, "Root");
        CommentResponse first = create(owner, root.id(), "First");
        CommentResponse second = create(other, root.id(), "Second");
        comments.deleteComment(owner, root.id());

        runBlockedPair(() -> {
            comments.deleteComment(owner, first.id());
            return null;
        }, () -> {
            comments.deleteComment(other, second.id());
            return null;
        });

        assertThat(ids()).isEmpty();
    }

    @Test
    void deletionBeforeEdit_shouldRejectTheEditAfterWaitingForTheLock() throws Exception {
        CommentResponse root = create(owner, null, "Root");
        create(other, root.id(), "Live reply");

        runBlockedPair(() -> {
            comments.deleteComment(owner, root.id());
            return null;
        }, () -> {
            assertThatThrownBy(() -> comments.updateComment(owner, new UpdateCommentRequest(root.id(), "Late edit")))
                    .isInstanceOf(CommentDeletedException.class);
            return null;
        }, true);

        assertThat(byId(root.id()).text()).isEqualTo("Комментарий удалён");
        assertThat(byId(root.id()).editedAt()).isNull();
    }

    @Test
    void editBeforeDeletion_shouldEraseTheEditedTextWithoutLosingTheEditTimestamp() throws Exception {
        CommentResponse root = create(owner, null, "Root");
        create(other, root.id(), "Live reply");

        runBlockedPair(() -> comments.updateComment(owner, new UpdateCommentRequest(root.id(), "Changed")), () -> {
            comments.deleteComment(owner, root.id());
            return null;
        });

        assertThat(byId(root.id()).text()).isEqualTo("Комментарий удалён");
        assertThat(byId(root.id()).editedAt()).isNotNull();
        assertThat(jdbc.queryForObject("select text from comments where id = ?", String.class, root.id())).isEmpty();
    }

    private void runBlockedPair(Supplier<?> first, Supplier<?> second) throws Exception {
        runBlockedPair(first, second, false);
    }

    private void runBlockedPair(Supplier<?> first, Supplier<?> second, boolean rollbackSecond) throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Integer> secondPid = new CompletableFuture<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> firstFuture = executor.submit(() -> new TransactionTemplate(transactionManager).execute(tx -> {
                spots.findByIdForUpdate(spotId).orElseThrow();
                locked.countDown();
                await(release);
                return first.get();
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> secondFuture = executor.submit(() -> new TransactionTemplate(transactionManager).execute(tx -> {
                secondPid.complete(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                Object result = second.get();
                if (rollbackSecond) {
                    // The negative callback already asserted the expected service exception.
                    assertThat(tx.isRollbackOnly()).isTrue();
                    tx.setRollbackOnly();
                }
                return result;
            }));
            int pid = secondPid.get(5, TimeUnit.SECONDS);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            boolean blocked;
            do {
                blocked = Boolean.TRUE.equals(jdbc.queryForObject(
                        "select cardinality(pg_blocking_pids(?)) > 0", Boolean.class, pid
                ));
                if (!blocked) {
                    Thread.sleep(20);
                }
            } while (!blocked && System.nanoTime() < deadline);
            assertThat(blocked).as("second writer waits for the PostgreSQL row lock").isTrue();
            release.countDown();
            firstFuture.get(10, TimeUnit.SECONDS);
            secondFuture.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for test writer");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private UUID insertUser(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into users (id, email, password_hash, display_name, role, enabled, created_at, updated_at)
                values (?, ?, 'unused', 'Comment author', ?, true, now(), now())
                """, id, id + "@example.test", role);
        return id;
    }

    private CommentResponse create(UUID user, Long parent, String text) {
        return comments.createComment(user, new CreateCommentRequest(parent, text), spotId);
    }

    private CommentResponse byId(Long id) {
        return comments.getCommentsBySpotId(owner, spotId).stream()
                .filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    private List<Long> ids() {
        return comments.getCommentsBySpotId(owner, spotId).stream().map(CommentResponse::id).toList();
    }

    private String bearer(UUID user, String role) {
        return "Bearer " + jwt.generateAccessToken(user, user + "@example.test", role);
    }
}
