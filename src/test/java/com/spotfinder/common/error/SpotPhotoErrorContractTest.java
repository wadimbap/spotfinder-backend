package com.spotfinder.common.error;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.spotfinder.auth.security.UserPrincipal;
import com.spotfinder.spot.controller.SpotPhotoController;
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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class SpotPhotoErrorContractTest {

    private final UUID userId = UUID.randomUUID();
    private final UUID spotId = UUID.randomUUID();
    private final UUID photoId = UUID.randomUUID();
    private final UserReader userReader = mock(UserReader.class);
    private final SpotRepository spotRepository = mock(SpotRepository.class);
    private final SpotPhotoRepository photoRepository = mock(SpotPhotoRepository.class);
    private final ObjectStorageService storage = mock(ObjectStorageService.class);
    private final SpotPhotoMapper mapper = mock(SpotPhotoMapper.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setRole(UserRole.USER);
        SpotEntity spot = new SpotEntity();
        spot.setId(spotId);
        spot.setCreatedBy(user);
        spot.setApproved(true);
        when(userReader.getByIdOrElseThrow(userId)).thenReturn(user);
        when(spotRepository.findById(spotId)).thenReturn(Optional.of(spot));
        when(spotRepository.findByIdForUpdate(spotId)).thenReturn(Optional.of(spot));

        UserPrincipal principal = new UserPrincipal(userId, "user@example.com", UserRole.USER);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null)
        );
        SpotPhotoServiceImpl service = new SpotPhotoServiceImpl(
                userReader, spotRepository, photoRepository, mapper, storage
        );
        mockMvc = MockMvcBuilders.standaloneSetup(new SpotPhotoController(service), new MultipartLimitController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void emptyPhoto_shouldReturnBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[0]);

        mockMvc.perform(multipart("/api/v1/spots/{spotId}/photos", spotId).file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PHOTO"));

        verifyNoInteractions(storage, photoRepository);
    }

    @Test
    void missingFilePart_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(multipart("/api/v1/spots/{spotId}/photos", spotId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_REQUEST_PART"));

        verifyNoInteractions(userReader, storage, photoRepository);
    }

    @Test
    void oversizedPhoto_shouldReturn413() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", new byte[5 * 1024 * 1024 + 1]
        );

        mockMvc.perform(multipart("/api/v1/spots/{spotId}/photos", spotId).file(file))
                .andExpect(status().is(413))
                .andExpect(jsonPath("$.code").value("PHOTO_TOO_LARGE"));

        verifyNoInteractions(storage, photoRepository);
    }

    @Test
    void frameworkMultipartLimit_shouldReturn413() throws Exception {
        mockMvc.perform(get("/test/multipart-size-limit"))
                .andExpect(status().is(413))
                .andExpect(jsonPath("$.code").value("PHOTO_TOO_LARGE"));
    }

    @Test
    void unsupportedPhotoType_shouldReturn415() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.txt", "text/plain", new byte[]{1});

        mockMvc.perform(multipart("/api/v1/spots/{spotId}/photos", spotId).file(file))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_PHOTO_TYPE"));

        verifyNoInteractions(storage, photoRepository);
    }

    @Test
    void missingPhotoType_shouldReturn415() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo", null, new byte[]{1});

        mockMvc.perform(multipart("/api/v1/spots/{spotId}/photos", spotId).file(file))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_PHOTO_TYPE"));

        verifyNoInteractions(storage, photoRepository);
    }

    @Test
    void missingPhotoOnDelete_shouldReturn404() throws Exception {
        mockMvc.perform(delete("/api/v1/spots/{spotId}/photos/{photoId}", spotId, photoId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SPOT_PHOTO_NOT_FOUND"));

        verifyNoInteractions(storage);
    }

    @Test
    void missingPhotoOnDownload_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/v1/spots/{spotId}/photos/{photoId}/content", spotId, photoId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SPOT_PHOTO_NOT_FOUND"));

        verifyNoInteractions(storage);
    }

    @Test
    void deletingAnotherUsersPhoto_shouldReturn403() throws Exception {
        UserEntity otherUser = new UserEntity();
        otherUser.setId(UUID.randomUUID());
        SpotPhotoEntity photo = new SpotPhotoEntity();
        photo.setCreatedBy(otherUser);
        when(photoRepository.findByIdAndSpotId(photoId, spotId)).thenReturn(Optional.of(photo));

        mockMvc.perform(delete("/api/v1/spots/{spotId}/photos/{photoId}", spotId, photoId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(storage);
    }

    @Test
    void storageFailure_shouldRemain500() throws Exception {
        doThrow(new IllegalStateException("Storage unavailable"))
                .when(storage).upload(any(), any(), any(Long.class), any());
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[]{1});

        mockMvc.perform(multipart("/api/v1/spots/{spotId}/photos", spotId).file(file))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("Unexpected server error"));

        verifyNoInteractions(photoRepository);
    }

    @RestController
    static class MultipartLimitController {

        @GetMapping("/test/multipart-size-limit")
        public void failWithMultipartLimit() {
            throw new MaxUploadSizeExceededException(5 * 1024 * 1024);
        }
    }
}
