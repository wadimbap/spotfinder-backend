package com.spotfinder.spot.controller;

import com.spotfinder.spot.dto.SpotMetadataResponse;
import com.spotfinder.spot.entity.SpotFeature;
import com.spotfinder.spot.entity.SpotType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/spots/metadata")
@RequiredArgsConstructor
public class SpotMetadataController {

    @GetMapping
    public SpotMetadataResponse getSpotMetadata() {
        return new SpotMetadataResponse(
                List.of(SpotType.values()),
                List.of(SpotFeature.values())
        );
    }
}
