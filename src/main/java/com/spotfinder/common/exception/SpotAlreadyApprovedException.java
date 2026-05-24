package com.spotfinder.common.exception;

import java.util.UUID;

public class SpotAlreadyApprovedException extends RuntimeException {

    public SpotAlreadyApprovedException(UUID spotId) {
        super("Approved spot cannot be edited: " + spotId);
    }
}
