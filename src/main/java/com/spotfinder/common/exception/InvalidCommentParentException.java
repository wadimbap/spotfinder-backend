package com.spotfinder.common.exception;

public class InvalidCommentParentException extends RuntimeException {
    public InvalidCommentParentException(Long id) {
        super("Parent comment does not belong to this spot: " + id);
    }
}
