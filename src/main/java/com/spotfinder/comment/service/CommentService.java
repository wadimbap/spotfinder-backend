package com.spotfinder.comment.service;

import com.spotfinder.comment.dto.CommentResponse;
import com.spotfinder.comment.dto.CreateCommentRequest;
import com.spotfinder.comment.dto.UpdateCommentRequest;
import com.spotfinder.comment.dto.mapper.CommentMapper;
import com.spotfinder.comment.entity.CommentEntity;
import com.spotfinder.comment.repository.CommentRepository;
import com.spotfinder.common.exception.CommentDeletedException;
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
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final UserReader userReader;
    private final SpotReader spotReader;
    private final CommentRepository commentRepository;
    private final CommentMapper commentMapper;

    @Transactional(readOnly = true)
    public List<CommentResponse> getCommentsBySpotId(UUID userId,
                                                     UUID spotId) {
        UserEntity user = userReader.getByIdOrElseThrow(userId);
        SpotEntity spot = spotReader.getByIdOrElseThrow(spotId);

        ensureCanAccessComments(user, spot);

        List<CommentEntity> comments = commentRepository.findAllBySpotIdOrderByCreatedAtAscIdAsc(spotId);

        return comments.stream().map(commentMapper::toResponse).toList();
    }


    @Transactional
    public CommentResponse createComment(UUID userId,
                                         CreateCommentRequest commentRequest,
                                         UUID spotId) {
        UserEntity user = userReader.getByIdOrElseThrow(userId);
        SpotEntity spot = spotReader.getByIdForUpdateOrElseThrow(spotId);

        ensureCanAccessComments(user, spot);

        CommentEntity entity = new CommentEntity();

        CommentEntity parent = null;

        if (commentRequest.parentCommentId() != null) {
            parent = commentRepository.findById(commentRequest.parentCommentId())
                    .orElseThrow(() -> new CommentNotFoundException(
                            commentRequest.parentCommentId()
                    ));

            if (!parent.getSpot().getId().equals(spot.getId())) {
                throw new InvalidCommentParentException(
                        commentRequest.parentCommentId()
                );
            }
            ensureNotDeleted(parent);
        }

        entity.setParentComment(parent);

        entity.setText(commentRequest.text());
        entity.setSpot(spot);
        entity.setCreatedBy(user);

        entity = commentRepository.save(entity);

        return commentMapper.toResponse(entity);
    }


    @Transactional
    public CommentResponse updateComment(UUID userId, UpdateCommentRequest request) {
        UserEntity user = userReader.getByIdOrElseThrow(userId);
        CommentEntity comment = getCommentForUpdate(user, request.id());

        if (!comment.getCreatedBy().getId().equals(userId)) {
            throw new CommentOwnershipException(userId);
        }

        ensureNotDeleted(comment);

        if (!Objects.equals(comment.getText(), request.text())) {
            comment.setText(request.text());
            comment.setEditedAt(Instant.now());
        }

        return commentMapper.toResponse(comment);
    }

    @Transactional
    public void deleteComment(UUID userId, Long commentId) {
        UserEntity user = userReader.getByIdOrElseThrow(userId);
        CommentEntity comment = getCommentForUpdate(user, commentId);

        if (!comment.getCreatedBy().getId().equals(userId)
                && user.getRole() != UserRole.MODERATOR && user.getRole() != UserRole.ADMIN) {
            throw new CommentOwnershipException(userId);
        }

        if (comment.getDeletedAt() == null) {
            comment.setText("");
            comment.setDeletedAt(Instant.now());
        }

        // Remove leaves first so the self-referencing FK never loses a live child.
        CommentEntity current = comment;
        while (current != null && current.getDeletedAt() != null
                && !commentRepository.existsByParentCommentId(current.getId())) {
            CommentEntity parent = current.getParentComment();
            commentRepository.delete(current);
            commentRepository.flush();
            current = parent;
        }
    }

    private CommentEntity getCommentForUpdate(UserEntity user, Long id) {
        UUID spotId = commentRepository.findSpotIdById(id)
                .orElseThrow(() -> new CommentNotFoundException(id));
        // All comment writes lock the same spot before loading mutable comment state.
        // A scalar lookup above avoids caching a stale entity while waiting for the lock.
        SpotEntity spot = spotReader.getByIdForUpdateOrElseThrow(spotId);
        ensureCanAccessComments(user, spot);
        return commentRepository.findById(id)
                .orElseThrow(() -> new CommentNotFoundException(id));
    }

    private void ensureNotDeleted(CommentEntity comment) {
        if (comment.getDeletedAt() != null) {
            throw new CommentDeletedException(comment.getId());
        }
    }

    private void ensureCanAccessComments(UserEntity user, SpotEntity spot) {
        boolean allowed = Boolean.TRUE.equals(spot.getApproved())
                || spot.getCreatedBy().getId().equals(user.getId())
                || user.getRole() == UserRole.MODERATOR
                || user.getRole() == UserRole.ADMIN;

        if (!allowed) {
            throw new SpotNotFoundException(spot.getId());
        }
    }
}
