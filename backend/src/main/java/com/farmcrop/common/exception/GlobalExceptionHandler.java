package com.farmcrop.common.exception;

import com.farmcrop.common.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Converts exceptions into the {@link ApiResponse} envelope. Unexpected errors are logged
 * server-side and returned as a generic message so internals never leak to clients.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String VALIDATION_MESSAGE = "Please correct the highlighted fields.";

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiResponse<Void>> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ApiResponse.error(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiResponse.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiResponse.FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(ApiResponse.validation(VALIDATION_MESSAGE, errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex) {
        List<ApiResponse.FieldError> errors = ex.getConstraintViolations().stream()
                .map(violation -> new ApiResponse.FieldError(lastPathNode(violation.getPropertyPath().toString()), violation.getMessage()))
                .toList();
        return ResponseEntity.badRequest().body(ApiResponse.validation(VALIDATION_MESSAGE, errors));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error("BAD_REQUEST", "The request is malformed or has invalid parameters."));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiResponse<Void>> handleUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(ApiResponse.error("FILE_TOO_LARGE", "The uploaded file is too large."));
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("UNAUTHORIZED", "Authentication is required."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("FORBIDDEN", "You do not have permission to perform this action."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("NOT_FOUND", "The requested resource was not found."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiResponse<Void>> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ApiResponse.error("METHOD_NOT_ALLOWED", "This HTTP method is not supported here."));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiResponse<Void>> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(ApiResponse.error("UNSUPPORTED_MEDIA_TYPE", "This content type is not supported."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.internalServerError().body(ApiResponse.error("INTERNAL_ERROR", "Something went wrong. Please try again later."));
    }

    private static String lastPathNode(String path) {
        int index = path.lastIndexOf('.');
        return index >= 0 ? path.substring(index + 1) : path;
    }
}
