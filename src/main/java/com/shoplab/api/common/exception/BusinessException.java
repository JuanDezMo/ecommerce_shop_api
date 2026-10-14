package com.shoplab.api.common.exception;

/**
 * Error de regla de negocio. La lanza el ServiceImpl y la traduce el GlobalExceptionHandler.
 * Ejemplo: new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, productId) → "Product 99 not found".
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode, Object... args) {
        super(errorCode.getMessage().formatted(args));
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
