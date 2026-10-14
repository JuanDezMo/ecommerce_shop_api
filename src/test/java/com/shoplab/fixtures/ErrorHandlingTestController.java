package com.shoplab.fixtures;

import com.shoplab.api.common.exception.BusinessException;
import com.shoplab.api.common.exception.ErrorCode;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller SOLO para tests del GlobalExceptionHandler (design D4).
 * Vive fuera de com.shoplab.api para que el escaneo de la aplicación no lo registre;
 * los tests lo importan explícitamente con @Import.
 */
@RestController
public class ErrorHandlingTestController {

    @PostMapping("/test-errors/body")
    public ResponseEntity<Void> body(@Valid @RequestBody ErrorHandlingTestRequest request) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("/test-errors/items/{id}")
    public ResponseEntity<Long> item(@PathVariable Long id) {
        return ResponseEntity.ok(id);
    }

    @GetMapping("/test-errors/product-not-found")
    public void productNotFound() {
        throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, 99L);
    }

    @GetMapping("/test-errors/locked")
    public void locked() {
        throw new BusinessException(ErrorCode.USER_LOCKED);
    }

    @GetMapping("/test-errors/boom")
    public void boom() {
        throw new IllegalStateException("database password=s3cr3t at com.shoplab.Internal");
    }
}
