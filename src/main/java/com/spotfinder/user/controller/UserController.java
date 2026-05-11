package com.spotfinder.user.controller;

import com.spotfinder.auth.security.UserPrincipal;
import com.spotfinder.user.dto.UpdateCurrentUserRequest;
import com.spotfinder.user.dto.UserResponse;
import com.spotfinder.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        UserResponse response = userService.getCurrentUser(principal.id());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateCurrentUser(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateCurrentUserRequest request
    ) {
        UserResponse response = userService.updateCurrentUser(principal.id(), request);
        return ResponseEntity.ok(response);
    }
}
