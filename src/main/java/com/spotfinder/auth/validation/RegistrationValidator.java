package com.spotfinder.auth.validation;

import com.spotfinder.auth.dto.RegisterRequest;
import com.spotfinder.common.exception.EmailAlreadyExistsException;
import com.spotfinder.common.exception.PasswordConfirmationMismatchException;
import com.spotfinder.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RegistrationValidator {

  private final UserRepository userRepository;

  public void validate(RegisterRequest registerRequest) {
    validatePasswordMatch(registerRequest);
    validateEmailIsUnique(registerRequest);
  }

  private void validatePasswordMatch(RegisterRequest registerRequest) {
    if (!registerRequest.password().equals(registerRequest.passwordConfirmation())) {
      throw new PasswordConfirmationMismatchException("Passwords should match");
    }
  }

  private void validateEmailIsUnique(RegisterRequest request) {
    if (userRepository.existsByEmail(request.email())) {
      throw new EmailAlreadyExistsException("Email already exists");
    }
  }
}
