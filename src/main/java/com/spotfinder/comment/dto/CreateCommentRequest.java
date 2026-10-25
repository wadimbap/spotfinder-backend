package com.spotfinder.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateCommentRequest(
        @Positive(message = "Parent comment id must be positive")
        Long parentCommentId,

        @Size(max = 3000, message = "Comment must be less than 3000 characters")
        @NotBlank(message = "Comment can't be blank")
        String text
) {
}
