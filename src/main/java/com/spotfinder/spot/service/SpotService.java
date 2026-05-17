package com.spotfinder.spot.service;

import com.spotfinder.common.exception.SpotNotFoundException;
import com.spotfinder.spot.dto.CreateSpotRequest;
import com.spotfinder.spot.dto.SpotResponse;
import com.spotfinder.spot.dto.mapper.SpotMapper;
import com.spotfinder.spot.entity.SpotEntity;
import com.spotfinder.spot.repository.SpotRepository;
import com.spotfinder.user.entity.UserEntity;
import com.spotfinder.user.service.UserReader;
import jakarta.transaction.Transactional;
import java.util.HashSet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SpotService {

    private final UserReader userReader;
    private final SpotRepository spotRepository;
    private final SpotMapper spotMapper;


    public SpotResponse createSpot(UUID userId, CreateSpotRequest request) {
        UserEntity user = userReader.getByIdOrElseThrow(userId);

        SpotEntity spot = new SpotEntity();
        spot.setName(request.name().trim());
        spot.setDescription(request.description());
        spot.setLatitude(request.latitude());
        spot.setLongitude(request.longitude());
        spot.setType(request.type());
        spot.setCreatedBy(user);
        spot.setApproved(Boolean.FALSE);

        spot.setFeatures(
                request.features() == null
                        ? new HashSet<>()
                        : new HashSet<>(request.features())
        );

        SpotEntity savedSpot = spotRepository.save(spot);

        return spotMapper.toResponse(savedSpot);
    }

    @Transactional
    public SpotResponse approveSpot(UUID spotId) {
        SpotEntity spot = getSpotByIdOrElseThrow(spotId);

        if (spot.getApproved().equals(Boolean.FALSE)) {
            spot.approve();
        }

        return spotMapper.toResponse(spot);
    }

    @Transactional
    public void deleteSpot(UUID spotId) {
        SpotEntity spot = getSpotByIdOrElseThrow(spotId);
        spotRepository.delete(spot);
    }

    private SpotEntity getSpotByIdOrElseThrow(UUID spotId) {
        return spotRepository.findById(spotId)
                .orElseThrow(() -> new SpotNotFoundException(spotId));
    }
}
