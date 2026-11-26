package com.farmcrop.common.exception;

import com.farmcrop.common.response.ApiResponse;
import org.springframework.http.HttpStatus;

import java.util.List;

/** Base class for expected, user-facing failures. The message must be safe to return to clients. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final List<ApiResponse.FieldError> fieldErrors;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, List.of());
    }

    public ApiException(HttpStatus status, String code, String message, List<ApiResponse.FieldError> fieldErrors) {
        super(message);
        this.status = status;
        this.code = code;
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public List<ApiResponse.FieldError> getFieldErrors() {
        return fieldErrors;
    }
}
