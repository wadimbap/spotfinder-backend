package com.spotfinder.spot.controller;

import com.spotfinder.auth.security.UserPrincipal;
import com.spotfinder.spot.dto.CreateSpotRequest;
import com.spotfinder.spot.dto.SpotResponse;
import com.spotfinder.spot.dto.UpdateSpotRequest;
import com.spotfinder.spot.service.impl.SpotService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    @GetMapping("/my")
    public ResponseEntity<List<SpotResponse>> getMySpots(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<SpotResponse> responses = spotService.getMySpots(principal.id());
        return ResponseEntity.ok(responses);
    }

    @PutMapping("/{spotId}")
    public ResponseEntity<SpotResponse> updateMyPendingSpot(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID spotId,
            @Valid @RequestBody UpdateSpotRequest request
    ) {
        SpotResponse response = spotService.updateMyPendingSpot(
                principal.id(),
                spotId,
                request);

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<SpotResponse>> getAllApprovedSpots(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<SpotResponse> responses = spotService.getAllApprovedSpots(principal.id());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{spotId}")
    public ResponseEntity<SpotResponse> getApprovedSpot(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID spotId
    ) {
        SpotResponse response = spotService.getBySpotIdAndApprovedIsTrue(principal.id(), spotId);
        return ResponseEntity.ok(response);
    }
}
