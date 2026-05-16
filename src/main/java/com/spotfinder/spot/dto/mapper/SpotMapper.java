package com.spotfinder.spot.dto.mapper;

import com.spotfinder.spot.dto.SpotResponse;
import com.spotfinder.spot.entity.SpotEntity;
import org.springframework.stereotype.Component;

@Component
public class SpotMapper {

    public SpotResponse toResponse(SpotEntity spot) {
        return new SpotResponse(
                spot.getId(),
                spot.getName(),
                spot.getDescription(),
                spot.getLatitude(),
                spot.getLongitude(),
                spot.getType(),
                spot.getFeatures(),
                spot.getCreatedAt(),
                spot.getUpdatedAt()
        );
    }
}
