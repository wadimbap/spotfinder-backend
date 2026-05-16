package com.spotfinder.spot.service;

import com.spotfinder.spot.dto.CreateSpotRequest;
import com.spotfinder.spot.dto.SpotResponse;
import com.spotfinder.spot.dto.mapper.SpotMapper;
import com.spotfinder.spot.entity.SpotEntity;
import com.spotfinder.spot.repository.SpotRepository;
import com.spotfinder.user.entity.UserEntity;
import com.spotfinder.user.service.UserReader;
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

        spot.setFeatures(
                request.features() == null
                        ? new HashSet<>()
                        : new HashSet<>(request.features())
        );

        SpotEntity savedSpot = spotRepository.save(spot);

        return spotMapper.toResponse(savedSpot);
    }
}
