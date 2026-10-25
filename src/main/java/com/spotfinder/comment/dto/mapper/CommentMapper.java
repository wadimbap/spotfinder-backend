package com.spotfinder.comment.dto.mapper;

import com.spotfinder.comment.dto.CommentResponse;
import com.spotfinder.comment.entity.CommentEntity;
import org.springframework.stereotype.Component;

@Component
public class CommentMapper {
        public CommentResponse toResponse(CommentEntity comment) {
            var parentComment = comment.getParentComment() != null ?  comment.getParentComment().getId() : null;
            return new CommentResponse(
                    comment.getId(),
                    parentComment,
                    comment.getText(),
                    comment.getCreatedBy().getId(),
                    comment.getCreatedAt(),
                    comment.getUpdatedAt()
            );
        }
}
