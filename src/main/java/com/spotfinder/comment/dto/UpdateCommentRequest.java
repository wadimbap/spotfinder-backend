package com.spotfinder.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpdateCommentRequest(
        @NotNull(message = "Id must be not null")
        @Positive(message = "Id must be not negative")
        Long id,
        @Size(max = 3000, message = "Comment must be less than 3000 characters")
        @NotBlank(message = "Comment can't be blank")
        String text
) {
}
