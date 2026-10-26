package com.spotfinder.spot.service.impl;

import com.spotfinder.common.exception.SpotNotFoundException;
import com.spotfinder.spot.entity.SpotEntity;
import com.spotfinder.spot.repository.SpotRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SpotReader {

    private final SpotRepository spotRepository;

    public SpotEntity getByIdOrElseThrow(UUID spotId) {
        return spotRepository.findById(spotId)
                .orElseThrow(() -> new SpotNotFoundException(spotId));
    }

    public SpotEntity getByIdForUpdateOrElseThrow(UUID spotId) {
        return spotRepository.findByIdForUpdate(spotId)
                .orElseThrow(() -> new SpotNotFoundException(spotId));
    }
}
