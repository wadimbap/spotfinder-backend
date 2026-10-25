package com.spotfinder.common.error;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.spotfinder.auth.security.UserPrincipal;
import com.spotfinder.common.exception.SpotAlreadyApprovedException;
import com.spotfinder.spot.controller.SpotController;
import com.spotfinder.spot.controller.SpotPhotoController;
import com.spotfinder.spot.service.SpotPhotoService;
import com.spotfinder.spot.service.impl.SpotService;
import com.spotfinder.user.entity.UserRole;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SpotApiErrorTest {

    private final UUID userId = UUID.randomUUID();
    private final UUID spotId = UUID.randomUUID();
    private final SpotService spotService = mock(SpotService.class);
    private final SpotPhotoService photoService = mock(SpotPhotoService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        UserPrincipal principal = new UserPrincipal(userId, "user@example.com", UserRole.USER);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null)
        );
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new SpotController(spotService), new SpotPhotoController(photoService)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void updateApprovedSpot_shouldReturnConflict() throws Exception {
        when(spotService.updateMyPendingSpot(any(), any(), any()))
                .thenThrow(new SpotAlreadyApprovedException(spotId));

        mockMvc.perform(put("/api/v1/spots/{spotId}", spotId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Spot","latitude":55.75,"longitude":37.62,
                                 "type":"STREET","features":["FLAT"]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SPOT_ALREADY_APPROVED"));
    }

    @Test
    void uploadToAnotherUsersSpot_shouldReturnForbidden() throws Exception {
        when(photoService.uploadPhoto(any(), any(), any()))
                .thenThrow(new AccessDeniedException("Only the spot owner can upload photos"));
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[]{1});

        mockMvc.perform(multipart("/api/v1/spots/{spotId}/photos", spotId).file(file))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }
}
