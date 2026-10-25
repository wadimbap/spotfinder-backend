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

public record UpdateSpotRequest(
        @NotBlank
        @Size(min = 2, max = 255)
        String name,

        @Size(max = 2000)
        String description,

        @NotNull
        @DecimalMin("-90.0")
        @DecimalMax("90.0")
        Double latitude,

        @NotNull
        @DecimalMin("-180.0")
        @DecimalMax("180.0")
        Double longitude,

        @NotNull
        SpotType type,

        @NotEmpty
        Set<@NotNull SpotFeature> features
) {
        public UpdateSpotRequest {
                if (name != null) {
                        name = name.trim();
                }
        }
}
