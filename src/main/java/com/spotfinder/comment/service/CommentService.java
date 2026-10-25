package com.spotfinder.comment.service;

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
import java.util.List;
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

        List<CommentEntity> comments = commentRepository.findAllBySpotIdOrderByCreatedAtAsc(spotId);

        return comments.stream().map(commentMapper::toResponse).toList();
    }


    @Transactional
    public CommentResponse createComment(UUID userId,
                                         CreateCommentRequest commentRequest,
                                         UUID spotId) {
        UserEntity user = userReader.getByIdOrElseThrow(userId);
        SpotEntity spot = spotReader.getByIdOrElseThrow(spotId);

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

        CommentEntity comment = commentRepository.findById(request.id())
                .orElseThrow(() -> new CommentNotFoundException(request.id()));

        ensureCanAccessComments(user, comment.getSpot());

        if (!comment.getCreatedBy().getId().equals(userId)) {
            throw new CommentOwnershipException(userId);
        }

        comment.setText(request.text());

        return commentMapper.toResponse(comment);
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
