package com.farmcrop.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Uniform response envelope for every /api/v1 endpoint.
 *
 * @param success whether the request succeeded
 * @param data    response payload (absent on errors)
 * @param message human-readable summary, safe to show to end users
 * @param code    stable machine-readable error code (absent on success)
 * @param errors  field-level validation errors (absent when not applicable)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        String message,
        String code,
        List<FieldError> errors,
        Instant timestamp
) {

    public record FieldError(String field, String message) {
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null, null, Instant.now());
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, data, message, null, null, Instant.now());
    }

    public static ApiResponse<Void> error(String code, String message) {
        return new ApiResponse<>(false, null, message, code, null, Instant.now());
    }

    public static ApiResponse<Void> validation(String message, List<FieldError> errors) {
        return new ApiResponse<>(false, null, message, "VALIDATION_FAILED", errors, Instant.now());
    }
}
