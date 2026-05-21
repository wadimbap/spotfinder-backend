package com.spotfinder.spot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spotfinder.common.exception.SpotNotFoundException;
import com.spotfinder.spot.dto.SpotPhotoContentResponse;
import com.spotfinder.spot.dto.SpotPhotoResponse;
import com.spotfinder.spot.dto.mapper.SpotPhotoMapper;
import com.spotfinder.spot.entity.SpotEntity;
import com.spotfinder.spot.entity.SpotPhotoEntity;
import com.spotfinder.spot.repository.SpotPhotoRepository;
import com.spotfinder.spot.repository.SpotRepository;
import com.spotfinder.storage.service.ObjectStorageService;
import com.spotfinder.storage.service.impl.SpotPhotoServiceImpl;
import com.spotfinder.user.entity.UserEntity;
import com.spotfinder.user.entity.UserRole;
import com.spotfinder.user.service.UserReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class SpotPhotoServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID OTHER_USER_ID = UUID.randomUUID();
    private static final UUID SPOT_ID = UUID.randomUUID();
    private static final UUID PHOTO_ID = UUID.randomUUID();

    @Mock
    UserReader userReader;

    @Mock
    SpotRepository spotRepository;

    @Mock
    SpotPhotoRepository spotPhotoRepository;

    @Mock
    SpotPhotoMapper spotPhotoMapper;

    @Mock
    ObjectStorageService objectStorageService;

    SpotPhotoServiceImpl spotPhotoService;

    @BeforeEach
    void setUp() {
        spotPhotoService = new SpotPhotoServiceImpl(
                userReader,
                spotRepository,
                spotPhotoRepository,
                spotPhotoMapper,
                objectStorageService
        );
    }

    @Test
    void uploadPhoto_shouldUploadFileSaveMetadataAndReturnResponse() {
        UserEntity user = userEntity(USER_ID, UserRole.USER);
        SpotEntity spot = spotEntity();
        MultipartFile file = imageFile();
        SpotPhotoResponse expectedResponse = photoResponse();

        when(userReader.getByIdOrElseThrow(USER_ID)).thenReturn(user);
        when(spotRepository.findById(SPOT_ID)).thenReturn(Optional.of(spot));
        when(spotPhotoRepository.save(any(SpotPhotoEntity.class)))
                .thenAnswer(invocation -> {
                    SpotPhotoEntity photo = invocation.getArgument(0);
                    photo.setId(PHOTO_ID);
                    return photo;
                });
        when(spotPhotoMapper.toResponse(any(SpotPhotoEntity.class))).thenReturn(expectedResponse);

        SpotPhotoResponse actualResponse = spotPhotoService.uploadPhoto(USER_ID, SPOT_ID, file);

        assertThat(actualResponse).isEqualTo(expectedResponse);

        ArgumentCaptor<String> objectKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(objectStorageService).upload(
                objectKeyCaptor.capture(),
                any(InputStream.class),
                eq(file.getSize()),
                eq("image/jpeg")
        );

        assertThat(objectKeyCaptor.getValue()).startsWith("spots/" + SPOT_ID + "/");
        assertThat(objectKeyCaptor.getValue()).endsWith("-photo.jpg");

        ArgumentCaptor<SpotPhotoEntity> photoCaptor = ArgumentCaptor.forClass(SpotPhotoEntity.class);
        verify(spotPhotoRepository).save(photoCaptor.capture());

        SpotPhotoEntity savedPhoto = photoCaptor.getValue();

        assertThat(savedPhoto.getSpot()).isEqualTo(spot);
        assertThat(savedPhoto.getCreatedBy()).isEqualTo(user);
        assertThat(savedPhoto.getObjectKey()).isEqualTo(objectKeyCaptor.getValue());
        assertThat(savedPhoto.getOriginalFilename()).isEqualTo("photo.jpg");
        assertThat(savedPhoto.getContentType()).isEqualTo("image/jpeg");
        assertThat(savedPhoto.getSizeBytes()).isEqualTo(file.getSize());

        verify(spotPhotoMapper).toResponse(savedPhoto);
    }

    @Test
    void uploadPhoto_shouldThrowExceptionWhenSpotNotFound() {
        MultipartFile file = imageFile();

        when(userReader.getByIdOrElseThrow(USER_ID)).thenReturn(userEntity(USER_ID, UserRole.USER));
        when(spotRepository.findById(SPOT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> spotPhotoService.uploadPhoto(USER_ID, SPOT_ID, file))
                .isInstanceOf(SpotNotFoundException.class)
                .hasMessageContaining(SPOT_ID.toString());

        verify(objectStorageService, never()).upload(
                any(),
                any(),
                any(Long.class),
                any()
        );
        verify(spotPhotoRepository, never()).save(any(SpotPhotoEntity.class));
    }

    @Test
    void uploadPhoto_shouldThrowExceptionWhenFileIsEmpty() {
        MultipartFile file = new MockMultipartFile(
                "file",
                "empty.jpg",
                "image/jpeg",
                new byte[0]
        );

        when(userReader.getByIdOrElseThrow(USER_ID)).thenReturn(userEntity(USER_ID, UserRole.USER));
        when(spotRepository.findById(SPOT_ID)).thenReturn(Optional.of(spotEntity()));

        assertThatThrownBy(() -> spotPhotoService.uploadPhoto(USER_ID, SPOT_ID, file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Photo file is required");

        verify(objectStorageService, never()).upload(any(), any(), any(Long.class), any());
        verify(spotPhotoRepository, never()).save(any(SpotPhotoEntity.class));
    }

    @Test
    void uploadPhoto_shouldThrowExceptionWhenFileIsTooLarge() {
        byte[] content = new byte[5 * 1024 * 1024 + 1];

        MultipartFile file = new MockMultipartFile(
                "file",
                "large.jpg",
                "image/jpeg",
                content
        );

        when(userReader.getByIdOrElseThrow(USER_ID)).thenReturn(userEntity(USER_ID, UserRole.USER));
        when(spotRepository.findById(SPOT_ID)).thenReturn(Optional.of(spotEntity()));

        assertThatThrownBy(() -> spotPhotoService.uploadPhoto(USER_ID, SPOT_ID, file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Photo file is too large");

        verify(objectStorageService, never()).upload(any(), any(), any(Long.class), any());
        verify(spotPhotoRepository, never()).save(any(SpotPhotoEntity.class));
    }

    @Test
    void uploadPhoto_shouldThrowExceptionWhenContentTypeIsUnsupported() {
        MultipartFile file = new MockMultipartFile(
                "file",
                "file.txt",
                "text/plain",
                "text".getBytes(StandardCharsets.UTF_8)
        );

        when(userReader.getByIdOrElseThrow(USER_ID)).thenReturn(userEntity(USER_ID, UserRole.USER));
        when(spotRepository.findById(SPOT_ID)).thenReturn(Optional.of(spotEntity()));

        assertThatThrownBy(() -> spotPhotoService.uploadPhoto(USER_ID, SPOT_ID, file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported photo content type");

        verify(objectStorageService, never()).upload(any(), any(), any(Long.class), any());
        verify(spotPhotoRepository, never()).save(any(SpotPhotoEntity.class));
    }

    @Test
    void getSpotPhotos_shouldReturnSpotPhotos() {
        SpotPhotoEntity firstPhoto = spotPhotoEntity(USER_ID);
        SpotPhotoEntity secondPhoto = spotPhotoEntity(USER_ID);

        SpotPhotoResponse firstResponse = photoResponse();
        SpotPhotoResponse secondResponse = photoResponse();

        when(userReader.getByIdOrElseThrow(USER_ID)).thenReturn(userEntity(USER_ID, UserRole.USER));
        when(spotRepository.findById(SPOT_ID)).thenReturn(Optional.of(spotEntity()));
        when(spotPhotoRepository.findAllBySpotIdOrderByCreatedAtAsc(SPOT_ID))
                .thenReturn(List.of(firstPhoto, secondPhoto));
        when(spotPhotoMapper.toResponse(firstPhoto)).thenReturn(firstResponse);
        when(spotPhotoMapper.toResponse(secondPhoto)).thenReturn(secondResponse);

        List<SpotPhotoResponse> responses = spotPhotoService.getSpotPhotos(USER_ID, SPOT_ID);

        assertThat(responses).isEqualTo(List.of(firstResponse, secondResponse));

        verify(userReader).getByIdOrElseThrow(USER_ID);
        verify(spotRepository).findById(SPOT_ID);
        verify(spotPhotoRepository).findAllBySpotIdOrderByCreatedAtAsc(SPOT_ID);
    }

    @Test
    void getPhotoContent_shouldReturnPhotoContent() {
        SpotPhotoEntity photo = spotPhotoEntity(USER_ID);
        ByteArrayInputStream inputStream = new ByteArrayInputStream("image".getBytes(StandardCharsets.UTF_8));

        when(userReader.getByIdOrElseThrow(USER_ID)).thenReturn(userEntity(USER_ID, UserRole.USER));
        when(spotPhotoRepository.findByIdAndSpotId(PHOTO_ID, SPOT_ID)).thenReturn(Optional.of(photo));
        when(objectStorageService.download(photo.getObjectKey())).thenReturn(inputStream);

        SpotPhotoContentResponse response = spotPhotoService.getPhotoContent(USER_ID, SPOT_ID, PHOTO_ID);

        assertThat(response.resource()).isNotNull();
        assertThat(response.originalFilename()).isEqualTo("photo.jpg");
        assertThat(response.contentType()).isEqualTo("image/jpeg");
        assertThat(response.sizeBytes()).isEqualTo(12345L);

        verify(userReader).getByIdOrElseThrow(USER_ID);
        verify(spotPhotoRepository).findByIdAndSpotId(PHOTO_ID, SPOT_ID);
        verify(objectStorageService).download(photo.getObjectKey());
    }

    @Test
    void deletePhoto_shouldDeletePhotoWhenUserIsOwner() {
        UserEntity user = userEntity(USER_ID, UserRole.USER);
        SpotPhotoEntity photo = spotPhotoEntity(USER_ID);

        when(userReader.getByIdOrElseThrow(USER_ID)).thenReturn(user);
        when(spotPhotoRepository.findByIdAndSpotId(PHOTO_ID, SPOT_ID)).thenReturn(Optional.of(photo));

        spotPhotoService.deletePhoto(USER_ID, SPOT_ID, PHOTO_ID);

        verify(objectStorageService).delete(photo.getObjectKey());
        verify(spotPhotoRepository).delete(photo);
    }

    @Test
    void deletePhoto_shouldDeletePhotoWhenUserIsAdmin() {
        UserEntity admin = userEntity(USER_ID, UserRole.ADMIN);
        SpotPhotoEntity photo = spotPhotoEntity(OTHER_USER_ID);

        when(userReader.getByIdOrElseThrow(USER_ID)).thenReturn(admin);
        when(spotPhotoRepository.findByIdAndSpotId(PHOTO_ID, SPOT_ID)).thenReturn(Optional.of(photo));

        spotPhotoService.deletePhoto(USER_ID, SPOT_ID, PHOTO_ID);

        verify(objectStorageService).delete(photo.getObjectKey());
        verify(spotPhotoRepository).delete(photo);
    }

    @Test
    void deletePhoto_shouldDeletePhotoWhenUserIsModerator() {
        UserEntity moderator = userEntity(USER_ID, UserRole.MODERATOR);
        SpotPhotoEntity photo = spotPhotoEntity(OTHER_USER_ID);

        when(userReader.getByIdOrElseThrow(USER_ID)).thenReturn(moderator);
        when(spotPhotoRepository.findByIdAndSpotId(PHOTO_ID, SPOT_ID)).thenReturn(Optional.of(photo));

        spotPhotoService.deletePhoto(USER_ID, SPOT_ID, PHOTO_ID);

        verify(objectStorageService).delete(photo.getObjectKey());
        verify(spotPhotoRepository).delete(photo);
    }

    @Test
    void deletePhoto_shouldThrowExceptionWhenUserCannotDeletePhoto() {
        UserEntity user = userEntity(USER_ID, UserRole.USER);
        SpotPhotoEntity photo = spotPhotoEntity(OTHER_USER_ID);

        when(userReader.getByIdOrElseThrow(USER_ID)).thenReturn(user);
        when(spotPhotoRepository.findByIdAndSpotId(PHOTO_ID, SPOT_ID)).thenReturn(Optional.of(photo));

        assertThatThrownBy(() -> spotPhotoService.deletePhoto(USER_ID, SPOT_ID, PHOTO_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("User is not allowed to delete this photo");

        verify(objectStorageService, never()).delete(any());
        verify(spotPhotoRepository, never()).delete(any(SpotPhotoEntity.class));
    }

    @Test
    void deletePhoto_shouldThrowExceptionWhenPhotoNotFound() {
        when(userReader.getByIdOrElseThrow(USER_ID)).thenReturn(userEntity(USER_ID, UserRole.USER));
        when(spotPhotoRepository.findByIdAndSpotId(PHOTO_ID, SPOT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> spotPhotoService.deletePhoto(USER_ID, SPOT_ID, PHOTO_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Spot photo not found");

        verify(objectStorageService, never()).delete(any());
        verify(spotPhotoRepository, never()).delete(any(SpotPhotoEntity.class));
    }

    private MultipartFile imageFile() {
        return new MockMultipartFile(
                "file",
                "photo.jpg",
                "image/jpeg",
                "image-content".getBytes(StandardCharsets.UTF_8)
        );
    }

    private SpotEntity spotEntity() {
        SpotEntity spot = new SpotEntity();
        spot.setId(SPOT_ID);
        spot.setName("Central Plaza");
        return spot;
    }

    private SpotPhotoEntity spotPhotoEntity(UUID createdByUserId) {
        SpotPhotoEntity photo = new SpotPhotoEntity();
        photo.setId(PHOTO_ID);
        photo.setSpot(spotEntity());
        photo.setObjectKey("spots/%s/photo.jpg".formatted(SPOT_ID));
        photo.setOriginalFilename("photo.jpg");
        photo.setContentType("image/jpeg");
        photo.setSizeBytes(12345L);
        photo.setCreatedBy(userEntity(createdByUserId, UserRole.USER));
        photo.setCreatedAt(Instant.now());
        return photo;
    }

    private UserEntity userEntity(UUID userId, UserRole role) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("user@example.com");
        user.setDisplayName("user_name");
        user.setRole(role);
        user.setEnabled(true);
        return user;
    }

    private SpotPhotoResponse photoResponse() {
        return new SpotPhotoResponse(
                PHOTO_ID,
                SPOT_ID,
                "photo.jpg",
                "image/jpeg",
                12345L,
                USER_ID,
                Instant.now()
        );
    }
}
