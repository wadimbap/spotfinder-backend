package com.spotfinder.spot.service.impl;

import com.spotfinder.common.exception.SpotNotFoundException;
import com.spotfinder.spot.entity.SpotEntity;
import com.spotfinder.spot.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SpotReader {

    private final SpotRepository spotRepository;

    public SpotEntity getByIdOrElseThrow(UUID spotId) {
        return spotRepository.findById(spotId)
                .orElseThrow(() -> new SpotNotFoundException(spotId));
    }
}
