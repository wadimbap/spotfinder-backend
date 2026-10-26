package com.spotfinder.common.exception;

public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Refresh session is invalid or expired");
    }
}
