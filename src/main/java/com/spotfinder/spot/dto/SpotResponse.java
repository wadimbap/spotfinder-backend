package com.spotfinder.spot.dto;

import com.spotfinder.spot.entity.SpotFeature;
import com.spotfinder.spot.entity.SpotType;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record SpotResponse(
        UUID id,
        String name,
        String description,
        Double latitude,
        Double longitude,
        SpotType type,
        Set<SpotFeature> features,
        Instant createdAt,
        Instant updatedAt
) {
}
