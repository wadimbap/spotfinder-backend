package com.spotfinder.spot.controller;

import com.spotfinder.auth.security.UserPrincipal;
import com.spotfinder.spot.dto.CreateSpotRequest;
import com.spotfinder.spot.dto.SpotResponse;
import com.spotfinder.spot.service.SpotService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@RequestMapping("/api/v1/spots")
public class SpotController {

    private final SpotService spotService;

    @PostMapping
    public ResponseEntity<SpotResponse> createSpot(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateSpotRequest request) {
        SpotResponse response = spotService.createSpot(principal.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN')")
    @PostMapping("/{spotId}/approve")
    public ResponseEntity<SpotResponse> approveSpot(@PathVariable UUID spotId) {
        SpotResponse response = spotService.approveSpot(spotId);
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{spotId}")
    public ResponseEntity<Void> deleteSpot(@PathVariable UUID spotId) {
        spotService.deleteSpot(spotId);
        return ResponseEntity.noContent().build();
    }
}
