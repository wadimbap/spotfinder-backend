package com.spotfinder.spot.dto;

import org.springframework.core.io.Resource;

public record SpotPhotoContentResponse(
        Resource resource,
        String originalFilename,
        String contentType,
        long sizeBytes
) {
}
