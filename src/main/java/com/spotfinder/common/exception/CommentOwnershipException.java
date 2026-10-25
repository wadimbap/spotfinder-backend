package com.spotfinder.common.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.FORBIDDEN, reason = "Вы не можете изменить чужой комментарий")
public class CommentOwnershipException extends RuntimeException {
    public CommentOwnershipException(UUID userId) {
        super("User with id=" + userId + " is not the owner of comment");
    }
}
