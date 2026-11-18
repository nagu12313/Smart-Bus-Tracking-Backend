package com.smartbus.exception;

import com.smartbus.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler — converts exceptions to clean JSON error responses.
 * All errors follow the standard ApiResponse<T> format.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /** 404 — Resource not found */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /** 400 — Bad request / business rule violation */
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(BadRequestException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /** 400 — Validation errors (@Valid annotation failures) */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationErrors(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        log.warn("Validation failed: {}", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Map<String, String>>builder()
                        .success(false)
                        .message("Validation failed. Please check the input fields.")
                        .data(errors)
                        .build());
    }

    /** 401 — Wrong credentials */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Invalid email or password."));
    }

    /** 403 — Access denied */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Access denied. You do not have permission."));
    }

    /** 409 — Conflict (duplicate entry) */
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicate(DuplicateResourceException ex) {
        log.warn("Duplicate resource: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /** 409 — Database integrity / foreign key constraint violation */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(org.springframework.dao.DataIntegrityViolationException ex) {
        log.warn("Database integrity violation: {}", ex.getMessage());

        String rootMsg = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        String userFriendlyMsg = "Operation cannot be completed because this record is referenced by other data or violates a unique constraint.";

        if (rootMsg != null) {
            String lower = rootMsg.toLowerCase();
            if (lower.contains("duplicate entry")) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)duplicate entry '([^']+)' for key '([^']+)'").matcher(rootMsg);
                if (m.find()) {
                    String val = m.group(1);
                    String key = m.group(2);
                    String cleanKey = key.contains(".") ? key.substring(key.lastIndexOf(".") + 1) : key;
                    userFriendlyMsg = String.format("A record with %s '%s' already exists. Please choose a unique value.", cleanKey.replace("_", " "), val);
                } else {
                    userFriendlyMsg = "A record with this unique identifier already exists. Please check your input.";
                }
            } else if (lower.contains("cannot delete or update a parent row") || lower.contains("foreign key constraint fails")) {
                if (lower.contains("`trips`") || lower.contains(".trips")) {
                    userFriendlyMsg = "Cannot delete this record because it is referenced in trip history. Please reassign or remove the associated trips first, or update its status to INACTIVE.";
                } else if (lower.contains("`students`") || lower.contains(".students")) {
                    userFriendlyMsg = "Cannot delete this record because students are currently assigned to it. Please reassign the students first.";
                } else if (lower.contains("`buses`") || lower.contains(".buses")) {
                    userFriendlyMsg = "Cannot delete this record because it is currently assigned to a bus. Please reassign the bus first.";
                } else if (lower.contains("`attendance`") || lower.contains(".attendance")) {
                    userFriendlyMsg = "Cannot delete this record because it has linked attendance records.";
                } else if (lower.contains("`route_stops`") || lower.contains(".route_stops")) {
                    userFriendlyMsg = "Cannot delete this record because it is currently part of an active route.";
                } else {
                    userFriendlyMsg = "Cannot delete this record because other active records depend on it. Please remove or reassign linked records first.";
                }
            } else if (lower.contains("cannot add or update a child row")) {
                userFriendlyMsg = "The referenced associated record (such as bus, driver, or route) does not exist.";
            }
        }

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(userFriendlyMsg));
    }

    /** 500 — Unexpected errors */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unexpected error: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred. Please try again."));
    }
}
