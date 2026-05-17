package com.spotfinder.spot.controller;

import com.spotfinder.auth.security.UserPrincipal;
import com.spotfinder.spot.dto.CreateSpotRequest;
import com.spotfinder.spot.dto.SpotResponse;
import com.spotfinder.spot.service.SpotService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/spots")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN')")
public class SpotAdminController {

    private final SpotService spotService;

    @PostMapping
    public ResponseEntity<SpotResponse> createApprovedSpot(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateSpotRequest request
    ) {
        SpotResponse response = spotService.createApprovedSpot(principal.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<SpotResponse>> getAllSpots() {
        List<SpotResponse> responses = spotService.getAllSpotsForAdmin();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{spotId}")
    public ResponseEntity<SpotResponse> getSpot(@PathVariable UUID spotId) {
        SpotResponse response = spotService.getSpotByIdForAdmin(spotId);
        return ResponseEntity.ok(response);
    }

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
