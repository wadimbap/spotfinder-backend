package com.spotfinder.spot.controller;


import com.spotfinder.auth.security.UserPrincipal;
import com.spotfinder.spot.dto.SpotPhotoResponse;
import com.spotfinder.spot.service.SpotPhotoService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/spots/{spotId}/photos")
public class SpotPhotoController {

    private final SpotPhotoService spotPhotoService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SpotPhotoResponse> uploadPhoto(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID spotId,
            @RequestPart("file") MultipartFile file
    ) {
        SpotPhotoResponse response = spotPhotoService.uploadPhoto(
                principal.id(),
                spotId,
                file
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<SpotPhotoResponse>> getSpotPhotos(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID spotId
    ) {
        List<SpotPhotoResponse> response = spotPhotoService.getSpotPhotos(
                principal.id(),
                spotId
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{photoId}/content")
    public ResponseEntity<Resource> getPhotoContent(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID spotId,
            @PathVariable UUID photoId
    ) {
        var response = spotPhotoService.getPhotoContent(
                principal.id(),
                spotId,
                photoId
        );

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(response.contentType()))
                .contentLength(response.sizeBytes())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(response.originalFilename())
                                .build()
                                .toString()
                )
                .body(response.resource());
    }

    @DeleteMapping("/{photoId}")
    public ResponseEntity<Void> deletePhoto(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID spotId,
            @PathVariable UUID photoId
    ) {
        spotPhotoService.deletePhoto(
                principal.id(),
                spotId,
                photoId
        );

        return ResponseEntity.noContent().build();
    }
}
