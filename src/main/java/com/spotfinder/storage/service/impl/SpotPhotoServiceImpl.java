package com.spotfinder.storage.service.impl;

import com.spotfinder.common.exception.SpotNotFoundException;
import com.spotfinder.spot.dto.SpotPhotoContentResponse;
import com.spotfinder.spot.dto.SpotPhotoResponse;
import com.spotfinder.spot.dto.mapper.SpotPhotoMapper;
import com.spotfinder.spot.entity.SpotEntity;
import com.spotfinder.spot.entity.SpotPhotoEntity;
import com.spotfinder.spot.repository.SpotPhotoRepository;
import com.spotfinder.spot.repository.SpotRepository;
import com.spotfinder.spot.service.SpotPhotoService;
import com.spotfinder.storage.service.ObjectStorageService;
import com.spotfinder.user.entity.UserEntity;
import com.spotfinder.user.entity.UserRole;
import com.spotfinder.user.service.UserReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class SpotPhotoServiceImpl implements SpotPhotoService {

    private static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final UserReader userReader;
    private final SpotRepository spotRepository;
    private final SpotPhotoRepository spotPhotoRepository;
    private final SpotPhotoMapper spotPhotoMapper;
    private final ObjectStorageService objectStorageService;

    @Override
    @Transactional
    public SpotPhotoResponse uploadPhoto(UUID userId, UUID spotId, MultipartFile file) {
        UserEntity user = userReader.getByIdOrElseThrow(userId);
        SpotEntity spot = getSpotByIdOrElseThrow(spotId);

        validateFile(file);

        String contentType = file.getContentType();
        String objectKey = buildObjectKey(spotId, file.getOriginalFilename());

        try {
            objectStorageService.upload(
                    objectKey,
                    file.getInputStream(),
                    file.getSize(),
                    contentType
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read uploaded file", exception);
        }

        SpotPhotoEntity photo = new SpotPhotoEntity();
        photo.setSpot(spot);
        photo.setObjectKey(objectKey);
        photo.setOriginalFilename(file.getOriginalFilename());
        photo.setContentType(contentType);
        photo.setSizeBytes(file.getSize());
        photo.setCreatedBy(user);

        SpotPhotoEntity savedPhoto = spotPhotoRepository.save(photo);

        return spotPhotoMapper.toResponse(savedPhoto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SpotPhotoResponse> getSpotPhotos(UUID userId, UUID spotId) {
        userReader.getByIdOrElseThrow(userId);
        getSpotByIdOrElseThrow(spotId);

        return spotPhotoRepository.findAllBySpotIdOrderByCreatedAtAsc(spotId)
                .stream()
                .map(spotPhotoMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SpotPhotoContentResponse getPhotoContent(UUID userId, UUID spotId, UUID photoId) {
        userReader.getByIdOrElseThrow(userId);

        SpotPhotoEntity photo = getPhotoByIdAndSpotIdOrElseThrow(photoId, spotId);
        InputStream inputStream = objectStorageService.download(photo.getObjectKey());

        return new SpotPhotoContentResponse(
                new InputStreamResource(inputStream),
                photo.getOriginalFilename(),
                photo.getContentType(),
                photo.getSizeBytes()
        );
    }

    @Override
    @Transactional
    public void deletePhoto(UUID userId, UUID spotId, UUID photoId) {
        UserEntity user = userReader.getByIdOrElseThrow(userId);
        SpotPhotoEntity photo = getPhotoByIdAndSpotIdOrElseThrow(photoId, spotId);

        if (!canDeletePhoto(user, photo)) {
            throw new IllegalStateException("User is not allowed to delete this photo");
        }

        objectStorageService.delete(photo.getObjectKey());
        spotPhotoRepository.delete(photo);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Photo file is required");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("Photo file is too large");
        }

        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("Unsupported photo content type");
        }
    }

    private String buildObjectKey(UUID spotId, String originalFilename) {
        String safeFilename = originalFilename == null
                ? "photo"
                : originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");

        return "spots/%s/%s-%s".formatted(
                spotId,
                UUID.randomUUID(),
                safeFilename
        );
    }

    private boolean canDeletePhoto(UserEntity user, SpotPhotoEntity photo) {
        return photo.getCreatedBy().getId().equals(user.getId())
                || user.getRole() == UserRole.ADMIN
                || user.getRole() == UserRole.MODERATOR;
    }

    private SpotEntity getSpotByIdOrElseThrow(UUID spotId) {
        return spotRepository.findById(spotId)
                .orElseThrow(() -> new SpotNotFoundException(spotId));
    }

    private SpotPhotoEntity getPhotoByIdAndSpotIdOrElseThrow(UUID photoId, UUID spotId) {
        return spotPhotoRepository.findByIdAndSpotId(photoId, spotId)
                .orElseThrow(() -> new IllegalArgumentException("Spot photo not found"));
    }
}
