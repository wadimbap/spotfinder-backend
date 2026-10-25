package com.spotfinder.comment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spotfinder.comment.dto.CommentResponse;
import com.spotfinder.comment.dto.CreateCommentRequest;
import com.spotfinder.comment.dto.UpdateCommentRequest;
import com.spotfinder.comment.dto.mapper.CommentMapper;
import com.spotfinder.comment.entity.CommentEntity;
import com.spotfinder.comment.repository.CommentRepository;
import com.spotfinder.common.exception.CommentNotFoundException;
import com.spotfinder.common.exception.CommentOwnershipException;
import com.spotfinder.common.exception.InvalidCommentParentException;
import com.spotfinder.common.exception.SpotNotFoundException;
import com.spotfinder.spot.entity.SpotEntity;
import com.spotfinder.spot.service.impl.SpotReader;
import com.spotfinder.user.entity.UserEntity;
import com.spotfinder.user.entity.UserRole;
import com.spotfinder.user.service.UserReader;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID OTHER_USER_ID = UUID.randomUUID();
    private static final UUID SPOT_ID = UUID.randomUUID();
    private static final UUID OTHER_SPOT_ID = UUID.randomUUID();
    private static final Long COMMENT_ID = 10L;
    private static final Long PARENT_COMMENT_ID = 11L;

    @Mock
    UserReader userReader;

    @Mock
    SpotReader spotReader;

    @Mock
    CommentRepository commentRepository;

    @Mock
    CommentMapper commentMapper;

    CommentService commentService;

    @BeforeEach
    void setUp() {
        commentService = new CommentService(userReader, spotReader, commentRepository, commentMapper);
    }

    @Test
    void createComment_shouldCreateRootComment() {
        UserEntity user = user(USER_ID);
        SpotEntity spot = spot(SPOT_ID, true, OTHER_USER_ID);
        CommentResponse expected = response(COMMENT_ID, null, USER_ID);

        when(userReader.getByIdOrElseThrow(USER_ID)).thenReturn(user);
        when(spotReader.getByIdOrElseThrow(SPOT_ID)).thenReturn(spot);
        when(commentRepository.save(any(CommentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(commentMapper.toResponse(any(CommentEntity.class))).thenReturn(expected);

        CommentResponse actual = commentService.createComment(
                USER_ID,
                new CreateCommentRequest(null, "Root comment"),
                SPOT_ID
        );

        assertThat(actual).isEqualTo(expected);
        ArgumentCaptor<CommentEntity> captor = ArgumentCaptor.forClass(CommentEntity.class);
        verify(commentRepository).save(captor.capture());
        assertThat(captor.getValue().getParentComment()).isNull();
        assertThat(captor.getValue().getSpot()).isSameAs(spot);
        assertThat(captor.getValue().getCreatedBy()).isSameAs(user);
    }

    @Test
    void createComment_shouldRejectParentFromAnotherSpot() {
        UserEntity user = user(USER_ID);
        SpotEntity spot = spot(SPOT_ID, true, OTHER_USER_ID);
        CommentEntity otherSpotParent = comment(
                PARENT_COMMENT_ID,
                spot(OTHER_SPOT_ID, true, OTHER_USER_ID),
                user(OTHER_USER_ID),
                null
        );

        when(userReader.getByIdOrElseThrow(USER_ID)).thenReturn(user);
        when(spotReader.getByIdOrElseThrow(SPOT_ID)).thenReturn(spot);
        when(commentRepository.findById(PARENT_COMMENT_ID)).thenReturn(Optional.of(otherSpotParent));

        assertThatThrownBy(() -> commentService.createComment(
                USER_ID,
                new CreateCommentRequest(PARENT_COMMENT_ID, "Reply"),
                SPOT_ID
        )).isInstanceOf(InvalidCommentParentException.class);

        verify(commentRepository, never()).save(any(CommentEntity.class));
    }

    @Test
    void createComment_shouldReturnNotFoundForMissingParent() {
        when(userReader.getByIdOrElseThrow(USER_ID)).thenReturn(user(USER_ID));
        when(spotReader.getByIdOrElseThrow(SPOT_ID)).thenReturn(spot(SPOT_ID, true, OTHER_USER_ID));
        when(commentRepository.findById(PARENT_COMMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.createComment(
                USER_ID,
                new CreateCommentRequest(PARENT_COMMENT_ID, "Reply"),
                SPOT_ID
        )).isInstanceOf(CommentNotFoundException.class);

        verify(commentRepository, never()).save(any(CommentEntity.class));
    }

    @Test
    void createComment_shouldHidePendingSpotFromRegularUser() {
        when(userReader.getByIdOrElseThrow(USER_ID)).thenReturn(user(USER_ID));
        when(spotReader.getByIdOrElseThrow(SPOT_ID)).thenReturn(spot(SPOT_ID, false, OTHER_USER_ID));

        assertThatThrownBy(() -> commentService.createComment(
                USER_ID,
                new CreateCommentRequest(null, "Comment"),
                SPOT_ID
        )).isInstanceOf(SpotNotFoundException.class);

        verify(commentRepository, never()).save(any(CommentEntity.class));
    }

    @Test
    void getCommentsBySpotId_shouldKeepAscendingCreatedOrder() {
        CommentEntity first = comment(COMMENT_ID, spot(SPOT_ID, true, OTHER_USER_ID), user(USER_ID), null);
        CommentEntity second = comment(PARENT_COMMENT_ID, spot(SPOT_ID, true, OTHER_USER_ID), user(USER_ID), null);
        CommentResponse firstResponse = response(COMMENT_ID, null, USER_ID);
        CommentResponse secondResponse = response(PARENT_COMMENT_ID, null, USER_ID);

        when(userReader.getByIdOrElseThrow(USER_ID)).thenReturn(user(USER_ID));
        when(spotReader.getByIdOrElseThrow(SPOT_ID)).thenReturn(spot(SPOT_ID, true, OTHER_USER_ID));
        when(commentRepository.findAllBySpotIdOrderByCreatedAtAsc(SPOT_ID)).thenReturn(List.of(first, second));
        when(commentMapper.toResponse(first)).thenReturn(firstResponse);
        when(commentMapper.toResponse(second)).thenReturn(secondResponse);

        assertThat(commentService.getCommentsBySpotId(USER_ID, SPOT_ID))
                .containsExactly(firstResponse, secondResponse);
    }

    @Test
    void updateComment_shouldChangeOnlyTextForOwner() {
        UserEntity user = user(USER_ID);
        SpotEntity spot = spot(SPOT_ID, true, OTHER_USER_ID);
        CommentEntity parent = comment(PARENT_COMMENT_ID, spot, user(OTHER_USER_ID), null);
        CommentEntity existing = comment(COMMENT_ID, spot, user, parent);

        when(userReader.getByIdOrElseThrow(USER_ID)).thenReturn(user);
        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(existing));
        when(commentMapper.toResponse(existing)).thenReturn(response(COMMENT_ID, PARENT_COMMENT_ID, USER_ID));

        commentService.updateComment(USER_ID, new UpdateCommentRequest(COMMENT_ID, "Updated"));

        assertThat(existing.getText()).isEqualTo("Updated");
        assertThat(existing.getParentComment()).isSameAs(parent);
        assertThat(existing.getCreatedBy()).isSameAs(user);
    }

    @Test
    void updateComment_shouldRejectNonOwnerWithoutChangingComment() {
        CommentEntity existing = comment(
                COMMENT_ID,
                spot(SPOT_ID, true, OTHER_USER_ID),
                user(OTHER_USER_ID),
                null
        );
        when(userReader.getByIdOrElseThrow(USER_ID)).thenReturn(user(USER_ID));
        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> commentService.updateComment(
                USER_ID,
                new UpdateCommentRequest(COMMENT_ID, "Updated")
        )).isInstanceOf(CommentOwnershipException.class);

        assertThat(existing.getText()).isEqualTo("Original text");
        verify(commentRepository, never()).save(any(CommentEntity.class));
    }

    private UserEntity user(UUID id) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setRole(UserRole.USER);
        user.setEmail(id + "@example.com");
        user.setDisplayName("user");
        user.setEnabled(true);
        return user;
    }

    private SpotEntity spot(UUID id, boolean approved, UUID ownerId) {
        SpotEntity spot = new SpotEntity();
        spot.setId(id);
        spot.setApproved(approved);
        spot.setCreatedBy(user(ownerId));
        return spot;
    }

    private CommentEntity comment(Long id, SpotEntity spot, UserEntity author, CommentEntity parent) {
        CommentEntity comment = new CommentEntity();
        comment.setId(id);
        comment.setSpot(spot);
        comment.setCreatedBy(author);
        comment.setParentComment(parent);
        comment.setText("Original text");
        comment.setCreatedAt(Instant.parse("2026-08-23T10:00:00Z"));
        return comment;
    }

    private CommentResponse response(Long id, Long parentId, UUID authorId) {
        return new CommentResponse(id, parentId, "Comment", authorId, Instant.now(), Instant.now());
    }
}
