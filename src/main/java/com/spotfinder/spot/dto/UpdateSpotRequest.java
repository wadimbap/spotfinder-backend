package com.spotfinder.spot.dto;

import com.spotfinder.spot.entity.SpotFeature;
import com.spotfinder.spot.entity.SpotType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record UpdateSpotRequest(
        @NotBlank
        String name,

        String description,

        @NotNull
        Double latitude,

        @NotNull
        Double longitude,

        @NotNull
        SpotType type,

        Set<SpotFeature> features
) {
}
