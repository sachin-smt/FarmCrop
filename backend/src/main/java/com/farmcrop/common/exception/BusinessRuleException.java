package com.farmcrop.common.exception;

import org.springframework.http.HttpStatus;

/** A request that is well-formed but violates a business rule (e.g. an invalid state transition). */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }

    public BusinessRuleException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }
}
