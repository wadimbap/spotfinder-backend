package com.spotfinder.common.error;

import com.spotfinder.common.exception.CommentNotFoundException;
import com.spotfinder.common.exception.CommentOwnershipException;
import com.spotfinder.common.exception.EmailAlreadyExistsException;
import com.spotfinder.common.exception.InvalidCommentParentException;
import com.spotfinder.common.exception.InvalidCredentialsException;
import com.spotfinder.common.exception.InvalidPhotoException;
import com.spotfinder.common.exception.PasswordConfirmationMismatchException;
import com.spotfinder.common.exception.PhotoTooLargeException;
import com.spotfinder.common.exception.SpotAlreadyApprovedException;
import com.spotfinder.common.exception.SpotNotFoundException;
import com.spotfinder.common.exception.SpotPhotoNotFoundException;
import com.spotfinder.common.exception.UnsupportedPhotoTypeException;
import com.spotfinder.common.exception.UserNotFoundException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception) {
        log.error("Unexpected error", exception);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(
                        "INTERNAL_SERVER_ERROR",
                        "Unexpected server error",
                        Map.of(), Instant.now()
                ));
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExists(
            EmailAlreadyExistsException exception
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        "EMAIL_ALREADY_EXISTS",
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(PasswordConfirmationMismatchException.class)
    public ResponseEntity<ErrorResponse> handlePasswordConfirmationMismatch(
            PasswordConfirmationMismatchException exception
    ) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(
                        "PASSWORD_CONFIRMATION_MISMATCH",
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException exception
    ) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(
                        "INVALID_CREDENTIALS",
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(
            UserNotFoundException exception
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(
                        "USER_NOT_FOUND",
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        Map<String, String> fieldErrors = new HashMap<>();

        exception.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.put(
                        error.getField(),
                        error.getDefaultMessage()
                ));

        return ResponseEntity.badRequest()
                .body(ErrorResponse.withFieldErrors(
                        "VALIDATION_ERROR",
                        "Validation failed",
                        fieldErrors
                ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequestBody(
            HttpMessageNotReadableException exception
    ) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(
                        "INVALID_REQUEST_BODY",
                        "Invalid request body"
                ));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException exception
    ) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(
                        "ACCESS_DENIED",
                        "Access denied"
                ));
    }

    @ExceptionHandler(InvalidPhotoException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPhoto(InvalidPhotoException exception) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("INVALID_PHOTO", exception.getMessage()));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequestPart(MissingServletRequestPartException exception) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(
                        "MISSING_REQUEST_PART",
                        "Required request part is missing: " + exception.getRequestPartName()
                ));
    }

    @ExceptionHandler(SpotPhotoNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSpotPhotoNotFound(SpotPhotoNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("SPOT_PHOTO_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler({PhotoTooLargeException.class, MaxUploadSizeExceededException.class})
    public ResponseEntity<ErrorResponse> handlePhotoTooLarge() {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(ErrorResponse.of("PHOTO_TOO_LARGE", "Photo file is too large"));
    }

    @ExceptionHandler(UnsupportedPhotoTypeException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedPhotoType(UnsupportedPhotoTypeException exception) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ErrorResponse.of("UNSUPPORTED_PHOTO_TYPE", exception.getMessage()));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandlerFound(NoHandlerFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(
                        "NOT_FOUND",
                        "Endpoint not found"
                ));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(
                        "NOT_FOUND",
                        "Endpoint not found"
                ));
    }

    @ExceptionHandler(SpotNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSpotNotFound(SpotNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(
                        "SPOT_NOT_FOUND",
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(SpotAlreadyApprovedException.class)
    public ResponseEntity<ErrorResponse> handleSpotAlreadyApproved(
            SpotAlreadyApprovedException exception
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        "SPOT_ALREADY_APPROVED",
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(CommentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCommentNotFound(
            CommentNotFoundException exception
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(
                        "COMMENT_NOT_FOUND",
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(InvalidCommentParentException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCommentParent(
            InvalidCommentParentException exception
    ) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(
                        "INVALID_COMMENT_PARENT",
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(CommentOwnershipException.class)
    public ResponseEntity<ErrorResponse> handleCommentOwnership(
            CommentOwnershipException exception
    ) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(
                        "COMMENT_ACCESS_DENIED",
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequestParameter(
            MissingServletRequestParameterException exception
    ) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(
                        "MISSING_REQUEST_PARAMETER",
                        "Required request parameter is missing: " + exception.getParameterName()
                ));
    }
}
