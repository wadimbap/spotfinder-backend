package com.spotfinder.spot.dto.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.spotfinder.spot.dto.SpotPhotoResponse;
import com.spotfinder.spot.entity.SpotEntity;
import com.spotfinder.spot.entity.SpotPhotoEntity;
import com.spotfinder.user.entity.UserEntity;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class SpotPhotoMapperTest {

    private final SpotPhotoMapper spotPhotoMapper = Mappers.getMapper(SpotPhotoMapper.class);

    @Test
    void toResponse_shouldMapEntityToResponse() {
        UUID photoId = UUID.randomUUID();
        UUID spotId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant createdAt = Instant.now();

        SpotEntity spot = new SpotEntity();
        spot.setId(spotId);

        UserEntity user = new UserEntity();
        user.setId(userId);

        SpotPhotoEntity entity = new SpotPhotoEntity();
        entity.setId(photoId);
        entity.setSpot(spot);
        entity.setObjectKey("spots/%s/photo.jpg".formatted(spotId));
        entity.setOriginalFilename("photo.jpg");
        entity.setContentType("image/jpeg");
        entity.setSizeBytes(12345L);
        entity.setCreatedBy(user);
        entity.setCreatedAt(createdAt);

        SpotPhotoResponse response = spotPhotoMapper.toResponse(entity);

        assertThat(response.id()).isEqualTo(photoId);
        assertThat(response.spotId()).isEqualTo(spotId);
        assertThat(response.originalFilename()).isEqualTo("photo.jpg");
        assertThat(response.contentType()).isEqualTo("image/jpeg");
        assertThat(response.sizeBytes()).isEqualTo(12345L);
        assertThat(response.createdByUserId()).isEqualTo(userId);
        assertThat(response.createdAt()).isEqualTo(createdAt);
    }
}
