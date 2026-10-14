package com.shoplab.api.common.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.shoplab.api.common.dto.ErrorResponse;
import com.shoplab.api.common.dto.FieldErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Traduce cualquier excepción a un ErrorResponse (§9 del documento de arquitectura).
 * No extiende ResponseEntityExceptionHandler: así todos los errores pasan por aquí y tienen el mismo formato.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String MALFORMED_JSON = "Malformed JSON request";

    // @Valid falló: se devuelven todos los campos con error, en orden determinista
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleInvalidFields(MethodArgumentNotValidException ex,
                                                             HttpServletRequest request) {
        List<FieldErrorResponse> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorResponse(error.getField(), error.getDefaultMessage()))
                .sorted(Comparator.comparing(FieldErrorResponse::field)
                        .thenComparing(FieldErrorResponse::message))
                .toList();
        return build(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage(), request, fieldErrors);
    }

    // Jackson no pudo leer el body: tipo incorrecto en un campo, o JSON mal formado
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                              HttpServletRequest request) {
        if (ex.getCause() instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
            FieldErrorResponse fieldError = new FieldErrorResponse(
                    toFieldPath(mismatch.getPath()), invalidTypeMessage(mismatch.getTargetType()));
            return build(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage(), request, List.of(fieldError));
        }
        return build(ErrorCode.VALIDATION_ERROR, MALFORMED_JSON, request, List.of());
    }

    // Parámetro de ruta con tipo incorrecto: GET /products/abc
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                            HttpServletRequest request) {
        FieldErrorResponse fieldError = new FieldErrorResponse(ex.getName(), invalidTypeMessage(ex.getRequiredType()));
        return build(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage(), request, List.of(fieldError));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex, HttpServletRequest request) {
        return build(ex.getErrorCode(), ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoRoute(HttpServletRequest request) {
        return build(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.getMessage(), request, List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpServletRequest request) {
        return build(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.getMessage(), request, List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaType(HttpServletRequest request) {
        return build(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE.getMessage(), request, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        // El detalle va al log, nunca al JSON
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage(), request, List.of());
    }

    private ResponseEntity<ErrorResponse> build(ErrorCode code, String message, HttpServletRequest request,
                                                List<FieldErrorResponse> fieldErrors) {
        ErrorResponse body = new ErrorResponse(Instant.now(), code.getStatus().value(), code.name(),
                message, request.getRequestURI(), fieldErrors);
        return ResponseEntity.status(code.getStatus()).body(body);
    }

    // [items, 0, quantity] → "items[0].quantity" (misma notación que usa Spring en @Valid)
    private static String toFieldPath(List<JsonMappingException.Reference> path) {
        StringBuilder field = new StringBuilder();
        for (JsonMappingException.Reference ref : path) {
            if (ref.getFieldName() != null) {
                if (!field.isEmpty()) {
                    field.append('.');
                }
                field.append(ref.getFieldName());
            } else {
                field.append('[').append(ref.getIndex()).append(']');
            }
        }
        return field.toString();
    }

    private static String invalidTypeMessage(Class<?> type) {
        if (type == null) {
            return "has an invalid value";
        }
        if (type.isEnum()) {
            String allowed = Arrays.stream(type.getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
            return "must be one of: " + allowed;
        }
        if (type == Integer.class || type == int.class || type == Long.class || type == long.class) {
            return "must be an integer";
        }
        if (type == Boolean.class || type == boolean.class) {
            return "must be true or false";
        }
        if (type == LocalDate.class) {
            return "must be a valid date (yyyy-MM-dd)";
        }
        return "has an invalid value";
    }
}
