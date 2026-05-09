package com.spotfinder.auth.controller;

import com.spotfinder.auth.dto.AuthResponse;
import com.spotfinder.auth.dto.RegisterRequest;
import com.spotfinder.auth.service.RegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthRestController {

  private final RegistrationService registrationService;

  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest registerRequest) {
    AuthResponse authResponse = registrationService.register(registerRequest);
    return ResponseEntity.status(HttpStatus.CREATED).body(authResponse);
  }
}
