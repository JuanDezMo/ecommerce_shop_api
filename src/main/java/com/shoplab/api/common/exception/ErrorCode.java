package com.shoplab.api.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Catálogo de errores de la API (§9.1 del documento de arquitectura).
 * Cada valor define su status HTTP y su mensaje; los mensajes con %d reciben el id del recurso.
 * Si agregas un valor, agrégalo también a docs/contracts/error-response.schema.json.
 */
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Request has invalid fields"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password"),
    USER_LOCKED(HttpStatus.LOCKED, "User is locked"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email is already registered"),
    UNDERAGE_USER(HttpStatus.UNPROCESSABLE_ENTITY, "User must be at least 18 years old"),
    TERMS_NOT_ACCEPTED(HttpStatus.UNPROCESSABLE_ENTITY, "Terms and conditions must be accepted"),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "Product %d not found"),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "Insufficient stock for product %d"),
    EMPTY_ORDER(HttpStatus.BAD_REQUEST, "Order must contain at least one item"),
    DUPLICATE_ORDER_ITEM(HttpStatus.BAD_REQUEST, "Product %d appears more than once in the order"),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "Order %d not found"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found"),
    AUTH_TOKEN_MISSING(HttpStatus.UNAUTHORIZED, "Authentication token is missing"),
    AUTH_TOKEN_MALFORMED(HttpStatus.UNAUTHORIZED, "Authentication token is malformed"),
    AUTH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Authentication token is invalid"),
    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Authentication token has expired"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access denied"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method not allowed"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content-Type must be application/json"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error"),
    // El ChaosFilter (perfil testing) responde con el status que llega en el header X-Force-Error
    FORCED_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Forced error for testing");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
