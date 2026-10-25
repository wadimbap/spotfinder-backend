package com.spotfinder.comment.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.spotfinder.auth.security.UserPrincipal;
import com.spotfinder.auth.security.JwtService;
import com.spotfinder.comment.dto.CommentResponse;
import com.spotfinder.comment.service.CommentService;
import com.spotfinder.common.error.GlobalExceptionHandler;
import com.spotfinder.common.exception.CommentNotFoundException;
import com.spotfinder.common.exception.CommentOwnershipException;
import com.spotfinder.common.exception.InvalidCommentParentException;
import com.spotfinder.user.entity.UserRole;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CommentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({CommentControllerTest.TestConfig.class, GlobalExceptionHandler.class})
class CommentControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID SPOT_ID = UUID.randomUUID();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    CommentService commentService;

    @BeforeEach
    void authenticate() {
        reset(commentService);
        UserPrincipal principal = new UserPrincipal(USER_ID, "user@example.com", UserRole.USER);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null)
        );
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createComment_shouldReturnCreatedResponse() throws Exception {
        CommentResponse response = response(1L, null);
        when(commentService.createComment(eq(USER_ID), any(), eq(SPOT_ID))).thenReturn(response);

        mockMvc.perform(post("/api/v1/comment")
                        .queryParam("spotId", SPOT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"parentCommentId":null,"text":"Root comment"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.parentId").doesNotExist());

        verify(commentService).createComment(eq(USER_ID), any(), eq(SPOT_ID));
    }

    @Test
    void createComment_shouldRejectBlankText() throws Exception {
        mockMvc.perform(post("/api/v1/comment")
                        .queryParam("spotId", SPOT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.text").exists());

        verifyNoInteractions(commentService);
    }

    @Test
    void createComment_shouldRejectTextLongerThan3000Characters() throws Exception {
        String text = "a".repeat(3001);

        mockMvc.perform(post("/api/v1/comment")
                        .queryParam("spotId", SPOT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"" + text + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.text").exists());

        verifyNoInteractions(commentService);
    }

    @Test
    void createComment_shouldRejectNonPositiveParentCommentId() throws Exception {
        mockMvc.perform(post("/api/v1/comment")
                        .queryParam("spotId", SPOT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"parentCommentId":0,"text":"Reply"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.parentCommentId").exists());

        verifyNoInteractions(commentService);
    }

    @Test
    void getComments_shouldRejectMissingSpotId() throws Exception {
        mockMvc.perform(get("/api/v1/comment"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(commentService);
    }

    @Test
    void updateComment_shouldReturnForbiddenForNonOwner() throws Exception {
        when(commentService.updateComment(eq(USER_ID), any()))
                .thenThrow(new CommentOwnershipException(USER_ID));

        mockMvc.perform(put("/api/v1/comment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":1,"text":"Updated"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void createComment_shouldReturnNotFoundForMissingParent() throws Exception {
        when(commentService.createComment(eq(USER_ID), any(), eq(SPOT_ID)))
                .thenThrow(new CommentNotFoundException(99L));

        mockMvc.perform(post("/api/v1/comment")
                        .queryParam("spotId", SPOT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"parentCommentId":99,"text":"Reply"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
    }

    @Test
    void createComment_shouldRejectParentFromAnotherSpot() throws Exception {
        when(commentService.createComment(eq(USER_ID), any(), eq(SPOT_ID)))
                .thenThrow(new InvalidCommentParentException(99L));

        mockMvc.perform(post("/api/v1/comment")
                        .queryParam("spotId", SPOT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"parentCommentId":99,"text":"Reply"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_COMMENT_PARENT"));
    }

    private CommentResponse response(Long id, Long parentId) {
        return new CommentResponse(
                id,
                parentId,
                "Root comment",
                USER_ID,
                Instant.parse("2026-08-23T10:00:00Z"),
                Instant.parse("2026-08-23T10:00:00Z")
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig {

        @Bean
        CommentService commentService() {
            return mock(CommentService.class);
        }

        @Bean
        JwtService jwtService() {
            return mock(JwtService.class);
        }
    }
}
