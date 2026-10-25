package com.spotfinder.common.exception;

public class UnsupportedPhotoTypeException extends RuntimeException {

    public UnsupportedPhotoTypeException() {
        super("Unsupported photo content type");
    }
}
