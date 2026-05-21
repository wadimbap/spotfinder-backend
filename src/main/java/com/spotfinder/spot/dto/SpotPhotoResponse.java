package com.spotfinder.spot.dto;

import java.time.Instant;
import java.util.UUID;

public record SpotPhotoResponse(
        UUID id,
        UUID spotId,
        String originalFilename,
        String contentType,
        Long sizeBytes,
        UUID createdByUserId,
        Instant createdAt
) {
}
