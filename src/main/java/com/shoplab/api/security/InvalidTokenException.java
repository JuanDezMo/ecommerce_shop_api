package com.shoplab.api.security;

import com.shoplab.api.common.exception.ErrorCode;

/**
 * Token rechazado, con el motivo como ErrorCode AUTH_TOKEN_*.
 * Oculta las excepciones de jjwt: el filtro solo conoce esta clase (§8.2).
 */
public class InvalidTokenException extends RuntimeException {

    private final ErrorCode errorCode;

    public InvalidTokenException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
