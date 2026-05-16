package com.spotfinder.spot.dto;

import com.spotfinder.spot.entity.SpotFeature;
import com.spotfinder.spot.entity.SpotType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record CreateSpotRequest(

        @NotBlank(message = "Spot name is required")
        @Size(min = 2, max = 255, message = "Spot name must be between 2 and 255 characters")
        String name,

        @Size(max = 2000, message = "Description must be less than 2000 characters")
        String description,

        @NotNull(message = "Latitude is required")
        @DecimalMin(value = "-90.0", message = "Latitude must be greater than or equal to -90")
        @DecimalMax(value = "90.0", message = "Latitude must be less than or equal to 90")
        Double latitude,

        @NotNull(message = "Longitude is required")
        @DecimalMin(value = "-180.0", message = "Longitude must be greater than or equal to -180")
        @DecimalMax(value = "180.0", message = "Longitude must be less than or equal to 180")
        Double longitude,

        @NotNull(message = "Spot type is required")
        SpotType type,

        @NotEmpty(message = "At least one spot feature is required")
        @NotNull
        Set<SpotFeature> features
) {
}
