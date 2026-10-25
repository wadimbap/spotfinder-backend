package com.spotfinder.comment.dto;

import java.time.Instant;
import java.util.UUID;

public record CommentResponse(
        Long id,
        Long parentId,
        String text,
        UUID createdByUserId,
        Instant createdAt,
        Instant updatedAt
) {
}
