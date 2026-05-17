package com.spotfinder.common.exception;

import java.util.UUID;

public class SpotNotFoundException extends RuntimeException {

    public SpotNotFoundException(UUID spotId) {
        super("Spot not found: " + spotId);
    }
}
