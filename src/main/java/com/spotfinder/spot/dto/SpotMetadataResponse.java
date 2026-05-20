package com.spotfinder.spot.dto;

import com.spotfinder.spot.entity.SpotFeature;
import com.spotfinder.spot.entity.SpotType;
import java.util.List;

public record SpotMetadataResponse(
        List<SpotType> types,
        List<SpotFeature> features
) {
}
