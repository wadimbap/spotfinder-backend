package com.spotfinder.comment.dto.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.spotfinder.comment.dto.CommentResponse;
import com.spotfinder.comment.entity.CommentEntity;
import com.spotfinder.spot.entity.SpotEntity;
import com.spotfinder.user.entity.UserEntity;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CommentMapperTest {

    private final CommentMapper mapper = new CommentMapper();

    @Test
    void toResponse_shouldMapRootCommentWithNullParentId() {
        UUID userId = UUID.randomUUID();
        CommentEntity comment = comment(1L, userId, null);

        CommentResponse response = mapper.toResponse(comment);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.parentId()).isNull();
        assertThat(response.text()).isEqualTo("Root comment");
        assertThat(response.createdByUserId()).isEqualTo(userId);
        assertThat(response.createdByDisplayName()).isEqualTo("Alex");
        assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-08-23T10:00:00Z"));
        assertThat(response.updatedAt()).isEqualTo(Instant.parse("2026-08-23T11:00:00Z"));
    }

    @Test
    void toResponse_shouldMapReplyParentId() {
        UUID userId = UUID.randomUUID();
        CommentEntity parent = comment(1L, userId, null);
        CommentEntity reply = comment(2L, userId, parent);

        CommentResponse response = mapper.toResponse(reply);

        assertThat(response.parentId()).isEqualTo(1L);
    }

    @Test
    void toResponse_shouldHideDeletedTextAndRetainAuthorAndTimestamps() {
        UUID userId = UUID.randomUUID();
        CommentEntity comment = comment(1L, userId, null);
        Instant editedAt = Instant.parse("2026-08-23T11:00:00Z");
        Instant deletedAt = Instant.parse("2026-08-23T12:00:00Z");
        comment.setEditedAt(editedAt);
        comment.setDeletedAt(deletedAt);

        CommentResponse response = mapper.toResponse(comment);

        assertThat(response.text()).isEqualTo("Комментарий удалён");
        assertThat(response.createdByUserId()).isEqualTo(userId);
        assertThat(response.createdByDisplayName()).isEqualTo("Alex");
        assertThat(response.editedAt()).isEqualTo(editedAt);
        assertThat(response.deletedAt()).isEqualTo(deletedAt);
        assertThat(response.createdAt()).isEqualTo(comment.getCreatedAt());
    }

    private CommentEntity comment(Long id, UUID userId, CommentEntity parent) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setDisplayName("Alex");

        CommentEntity comment = new CommentEntity();
        comment.setId(id);
        comment.setSpot(new SpotEntity());
        comment.setCreatedBy(user);
        comment.setParentComment(parent);
        comment.setText("Root comment");
        comment.setCreatedAt(Instant.parse("2026-08-23T10:00:00Z"));
        comment.setUpdatedAt(Instant.parse("2026-08-23T11:00:00Z"));
        return comment;
    }
}
