package com.spotfinder.spot.service.impl;

import com.spotfinder.common.exception.SpotNotFoundException;
import com.spotfinder.spot.dto.CreateSpotRequest;
import com.spotfinder.spot.dto.SpotResponse;
import com.spotfinder.spot.dto.mapper.SpotMapper;
import com.spotfinder.spot.entity.SpotEntity;
import com.spotfinder.spot.repository.SpotRepository;
import com.spotfinder.user.entity.UserEntity;
import com.spotfinder.user.service.UserReader;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SpotService {

    private final UserReader userReader;
    private final SpotRepository spotRepository;
    private final SpotMapper spotMapper;

    @Transactional
    public SpotResponse createSpot(UUID userId, CreateSpotRequest request) {
        return createSpot(userId, request, false);
    }

    @Transactional
    public SpotResponse createApprovedSpot(UUID userId, CreateSpotRequest request) {
        return createSpot(userId, request, true);
    }

    private SpotResponse createSpot(UUID userId, CreateSpotRequest request, boolean approved) {
        UserEntity user = userReader.getByIdOrElseThrow(userId);

        SpotEntity spot = new SpotEntity();
        spot.setName(request.name().trim());
        spot.setDescription(request.description());
        spot.setLatitude(request.latitude());
        spot.setLongitude(request.longitude());
        spot.setType(request.type());
        spot.setCreatedBy(user);
        spot.setApproved(approved);
        spot.setFeatures(
                request.features() == null
                        ? new HashSet<>()
                        : new HashSet<>(request.features())
        );

        SpotEntity savedSpot = spotRepository.save(spot);

        return spotMapper.toResponse(savedSpot);
    }

    @Transactional(readOnly = true)
    public List<SpotResponse> getAllApprovedSpots(UUID userId) {
        userReader.getByIdOrElseThrow(userId);

        return spotRepository.getAllApprovedSpots()
                .stream()
                .map(spotMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SpotResponse getBySpotIdAndApprovedIsTrue(UUID userId, UUID spotId) {
        userReader.getByIdOrElseThrow(userId);
        SpotEntity spot = spotRepository.findByIdAndApprovedTrue(spotId)
                .orElseThrow(() -> new SpotNotFoundException(spotId));

        return spotMapper.toResponse(spot);
    }

    @Transactional
    public List<SpotResponse> getMySpots(UUID userId) {
        userReader.getByIdOrElseThrow(userId);

        return spotRepository.findAllByCreatedByIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(spotMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SpotResponse> getAllPendingSpotsForAdmin() {

        return spotRepository.findAllByApprovedFalse()
                .stream()
                .map(spotMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SpotResponse getSpotByIdForAdmin(UUID spotId) {
        SpotEntity spot = getSpotByIdOrElseThrow(spotId);

        return spotMapper.toResponse(spot);
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
    public void rejectSpot(UUID spotId) {
        deleteSpotById(spotId);
    }

    @Transactional
    public void deleteSpot(UUID spotId) {
        deleteSpotById(spotId);
    }

    private void deleteSpotById(UUID spotId) {
        SpotEntity spot = getSpotByIdOrElseThrow(spotId);
        spotRepository.delete(spot);
    }

    private SpotEntity getSpotByIdOrElseThrow(UUID spotId) {
        return spotRepository.findById(spotId)
                .orElseThrow(() -> new SpotNotFoundException(spotId));
    }
}
