package com.spotfinder.common.exception;

public class CommentDeletedException extends RuntimeException {

    public CommentDeletedException(Long id) {
        super("Comment is deleted: " + id);
    }
}
