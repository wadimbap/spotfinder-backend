package com.spotfinder.common.exception;

public class PhotoTooLargeException extends RuntimeException {

    public PhotoTooLargeException() {
        super("Photo file is too large");
    }
}
