package com.spotfinder.spot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.spotfinder.auth.security.JwtService;
import com.spotfinder.comment.dto.CreateCommentRequest;
import com.spotfinder.comment.service.CommentService;
import com.spotfinder.spot.dto.UpdateSpotRequest;
import com.spotfinder.spot.entity.SpotFeature;
import com.spotfinder.spot.entity.SpotType;
import com.spotfinder.spot.service.impl.SpotService;
import com.spotfinder.storage.service.ObjectStorageService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties = "storage.minio.initialize-bucket=false")
class SpotRemoderationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    SpotService spots;
    @Autowired
    SpotPhotoService photos;
    @Autowired
    CommentService comments;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    MockMvc mvc;
    @Autowired
    JwtService jwt;
    @Autowired
    PlatformTransactionManager transactionManager;
    @MockitoBean
    ObjectStorageService storage;

    UUID owner;
    UUID other;
    UUID moderator;
    UUID spotId;
    UUID oldPhotoId;

    @BeforeEach
    void fixtures() {
        owner = insertUser("USER");
        other = insertUser("USER");
        moderator = insertUser("MODERATOR");
        spotId = UUID.randomUUID();
        oldPhotoId = UUID.randomUUID();
        jdbc.update("""
                insert into spots (id, name, latitude, longitude, type, approved, created_by_user_id,
                                   created_at, updated_at)
                values (?, 'Original spot', 55.75, 37.62, 'STREET', true, ?, now(), now())
                """, spotId, owner);
        jdbc.update("insert into spot_features (spot_id, feature) values (?, 'FLAT')", spotId);
        jdbc.update("""
                insert into spot_photos (id, spot_id, object_key, original_filename, content_type,
                                         size_bytes, created_by_user_id, created_at, updated_at)
                values (?, ?, ?, 'existing.jpg', 'image/jpeg', 5, ?, now(), now())
                """, oldPhotoId, spotId, oldPhotoId + ".jpg", owner);
        comments.createComment(other, new CreateCommentRequest(null, "Existing comment"), spotId);
        when(storage.download(any())).thenAnswer(invocation -> new ByteArrayInputStream(new byte[]{1, 2, 3}));
    }

    @Test
    void ownerEdit_shouldHideTheWholeSpotUntilModeratorApprovesAgain() throws Exception {
        mvc.perform(put("/api/v1/spots/{id}", spotId).header("Authorization", bearer(owner, "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(requestJson("Edited spot")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.approved").value(false));

        assertPendingAndPreserved(1);

        mvc.perform(post("/api/v1/admin/spots/{id}/approve", spotId)
                        .header("Authorization", bearer(owner, "USER"))).andExpect(status().isForbidden());
        assertThat(approved()).isFalse();
        mvc.perform(post("/api/v1/admin/spots/{id}/approve", spotId)
                        .header("Authorization", bearer(moderator, "MODERATOR"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/spots/{id}", spotId).header("Authorization", bearer(other, "USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Edited spot"));
        mvc.perform(get("/api/v1/spots/{id}/photos", spotId).header("Authorization", bearer(other, "USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/comment").param("spotId", spotId.toString())
                        .header("Authorization", bearer(other, "USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].text").value("Existing comment"));
    }

    @Test
    void successfulPhotoUpload_shouldReturnTheEntireSpotToPending() throws Exception {
        mvc.perform(multipart("/api/v1/spots/{id}/photos", spotId).file(image("new.jpg"))
                        .header("Authorization", bearer(owner, "USER"))).andExpect(status().isOk());

        assertPendingAndPreserved(2);
    }

    @Test
    void unchangedSave_shouldPreserveApprovalAndData() throws Exception {
        mvc.perform(put("/api/v1/spots/{id}", spotId).header("Authorization", bearer(owner, "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(requestJson("Original spot")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.approved").value(true));
        assertThat(approved()).isTrue();
    }

    @Test
    void moderatorEdit_shouldKeepApprovalButCannotUploadToAnotherOwnersSpot() throws Exception {
        mvc.perform(put("/api/v1/admin/spots/{id}", spotId)
                        .header("Authorization", bearer(moderator, "MODERATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(requestJson("Moderator edit")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.approved").value(true));
        mvc.perform(multipart("/api/v1/spots/{id}/photos", spotId).file(image("new.jpg"))
                        .header("Authorization", bearer(moderator, "MODERATOR"))).andExpect(status().isForbidden());
        assertThat(approved()).isTrue();
    }

    @Test
    void invalidOrFailedPhotoUpload_shouldPreserveApproval() throws Exception {
        mvc.perform(multipart("/api/v1/spots/{id}/photos", spotId)
                        .file(new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]))
                        .header("Authorization", bearer(owner, "USER"))).andExpect(status().isBadRequest());
        assertThat(approved()).isTrue();

        doThrow(new IllegalStateException("Storage unavailable"))
                .when(storage).upload(any(), any(), any(Long.class), any());
        assertThatThrownBy(() -> photos.uploadPhoto(owner, spotId, image("new.jpg")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(approved()).isTrue();
    }

    @Test
    void commitFailure_shouldRollBackApprovalAndPhotoMetadataTogether() {
        assertThatThrownBy(() -> photos.uploadPhoto(owner, spotId, image("x".repeat(300) + ".jpg")))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(approved()).isTrue();
        assertThat(photoCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void ownerMutationAfterApproval_shouldWaitForTheLockAndRequireModeration(boolean upload) throws Exception {
        jdbc.update("update spots set approved = false where id = ?", spotId);
        runBlockedPair(() -> spots.approveSpot(spotId), () -> upload
                ? photos.uploadPhoto(owner, spotId, image("new.jpg"))
                : spots.updateMySpot(owner, spotId, request("Changed after approval")));

        assertThat(approved()).isFalse();
        assertThat(photoCount()).isEqualTo(upload ? 2 : 1);
    }

    @Test
    void adminEditAfterOwnerMutation_shouldWaitAndPreservePendingStatus() throws Exception {
        runBlockedPair(() -> spots.updateMySpot(owner, spotId, request("Owner change")),
                () -> spots.updateSpotForAdmin(spotId, request("Moderator correction")));

        assertThat(approved()).isFalse();
        assertThat(jdbc.queryForObject("select name from spots where id = ?", String.class, spotId))
                .isEqualTo("Moderator correction");
    }

    private void assertPendingAndPreserved(int expectedPhotos) throws Exception {
        assertThat(approved()).isFalse();
        assertThat(photoCount()).isEqualTo(expectedPhotos);
        assertThat(jdbc.queryForObject("select count(*) from comments where spot_id = ?", Integer.class, spotId))
                .isEqualTo(1);
        mvc.perform(get("/api/v1/spots").header("Authorization", bearer(other, "USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == '" + spotId + "')]").isEmpty());
        mvc.perform(get("/api/v1/spots/{id}", spotId).header("Authorization", bearer(other, "USER")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/spots/my").header("Authorization", bearer(owner, "USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].approved").value(false));
        mvc.perform(get("/api/v1/admin/spots/pending").header("Authorization", bearer(moderator, "MODERATOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == '" + spotId + "')]").isNotEmpty());
        mvc.perform(get("/api/v1/spots/{id}/photos", spotId).header("Authorization", bearer(other, "USER")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/spots/{id}/photos/{photoId}/content", spotId, oldPhotoId)
                        .header("Authorization", bearer(other, "USER"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/spots/{id}/photos", spotId).header("Authorization", bearer(owner, "USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(expectedPhotos));
        mvc.perform(get("/api/v1/spots/{id}/photos/{photoId}/content", spotId, oldPhotoId)
                        .header("Authorization", bearer(moderator, "MODERATOR"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/comment").param("spotId", spotId.toString())
                        .header("Authorization", bearer(other, "USER"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/comment").param("spotId", spotId.toString())
                        .header("Authorization", bearer(owner, "USER"))).andExpect(status().isOk());
    }

    private void runBlockedPair(Supplier<?> first, Supplier<?> second) throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Integer> secondPid = new CompletableFuture<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> firstFuture = executor.submit(() -> new TransactionTemplate(transactionManager).execute(tx -> {
                Object result = first.get();
                locked.countDown();
                await(release);
                return result;
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> secondFuture = executor.submit(() -> new TransactionTemplate(transactionManager).execute(tx -> {
                secondPid.complete(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                return second.get();
            }));
            int pid = secondPid.get(5, TimeUnit.SECONDS);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            boolean blocked;
            do {
                blocked = Boolean.TRUE.equals(jdbc.queryForObject(
                        "select cardinality(pg_blocking_pids(?)) > 0", Boolean.class, pid));
                if (!blocked) {
                    Thread.sleep(20);
                }
            } while (!blocked && System.nanoTime() < deadline);
            assertThat(blocked).as("the second writer waits for the spot row lock").isTrue();
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

    private boolean approved() {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select approved from spots where id = ?", Boolean.class, spotId));
    }

    private int photoCount() {
        return jdbc.queryForObject("select count(*) from spot_photos where spot_id = ?", Integer.class, spotId);
    }

    private UUID insertUser(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into users (id, email, password_hash, display_name, role, enabled, created_at, updated_at)
                values (?, ?, 'unused', 'Spot owner', ?, true, now(), now())
                """, id, id + "@example.test", role);
        return id;
    }

    private UpdateSpotRequest request(String name) {
        return new UpdateSpotRequest(name, null, 55.75, 37.62, SpotType.STREET, Set.of(SpotFeature.FLAT));
    }

    private String requestJson(String name) {
        return """
                {"name":"%s","description":null,"latitude":55.75,"longitude":37.62,
                 "type":"STREET","features":["FLAT"]}
                """.formatted(name);
    }

    private MockMultipartFile image(String name) {
        return new MockMultipartFile("file", name, "image/jpeg", "photo".getBytes(StandardCharsets.UTF_8));
    }

    private String bearer(UUID user, String role) {
        return "Bearer " + jwt.generateAccessToken(user, user + "@example.test", role);
    }
}
