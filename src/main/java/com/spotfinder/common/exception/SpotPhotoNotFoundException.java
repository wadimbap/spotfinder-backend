package com.spotfinder.common.exception;

import java.util.UUID;

public class SpotPhotoNotFoundException extends RuntimeException {

    public SpotPhotoNotFoundException(UUID photoId) {
        super("Spot photo not found: " + photoId);
    }
}
