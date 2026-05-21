package com.spotfinder.spot.service;

import com.spotfinder.spot.dto.SpotPhotoContentResponse;
import com.spotfinder.spot.dto.SpotPhotoResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface SpotPhotoService {

    SpotPhotoResponse uploadPhoto(UUID userId, UUID spotId, MultipartFile file);

    List<SpotPhotoResponse> getSpotPhotos(UUID userId, UUID spotId);

    SpotPhotoContentResponse getPhotoContent(UUID userId, UUID spotId, UUID photoId);

    void deletePhoto(UUID userId, UUID spotId, UUID photoId);
}
