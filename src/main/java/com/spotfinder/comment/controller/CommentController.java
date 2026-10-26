package com.spotfinder.comment.controller;

import com.spotfinder.auth.security.UserPrincipal;
import com.spotfinder.comment.dto.CommentResponse;
import com.spotfinder.comment.dto.CreateCommentRequest;
import com.spotfinder.comment.dto.UpdateCommentRequest;
import com.spotfinder.comment.service.CommentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/comment")
@PreAuthorize("isAuthenticated()")
public class CommentController {

    private final CommentService commentService;

    @GetMapping
    public ResponseEntity<List<CommentResponse>> getSpotComments(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam UUID spotId
    ) {
        List<CommentResponse> comments =
                commentService.getCommentsBySpotId(principal.id(), spotId);

        return ResponseEntity.ok(comments);
    }

    @PostMapping
    public ResponseEntity<CommentResponse> createComment(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateCommentRequest commentRequest,
            @RequestParam UUID spotId
    ) {
        CommentResponse response = commentService.createComment(principal.id(), commentRequest, spotId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping
    public ResponseEntity<CommentResponse> updateComment(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateCommentRequest commentRequest
    ) {
        CommentResponse response = commentService.updateComment(principal.id(), commentRequest);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long commentId
    ) {
        commentService.deleteComment(principal.id(), commentId);
        return ResponseEntity.noContent().build();
    }
}
