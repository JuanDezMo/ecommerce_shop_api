package com.shoplab.api.testsupport;

import com.shoplab.api.testsupport.dto.ExpiredTokenRequest;
import com.shoplab.api.testsupport.dto.ExpiredTokenResponse;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Utilidades para las suites de automatización (§12). Solo existe con el perfil testing:
 * sin él, /api/v1/test/** responde 404 porque no hay controller.
 * NUNCA activar este perfil en un entorno público: /test/reset borra todo sin autenticación.
 */
@RestController
@Profile("testing")
@RequestMapping("/api/v1/test")
public class TestSupportController {

    private final TestDataResetService resetService;
    private final ExpiredTokenFactory expiredTokenFactory;

    public TestSupportController(TestDataResetService resetService, ExpiredTokenFactory expiredTokenFactory) {
        this.resetService = resetService;
        this.expiredTokenFactory = expiredTokenFactory;
    }

    /** Vuelve a los datos semilla y reinicia los ids. Llamarlo antes de cada suite. */
    @PostMapping("/reset")
    public ResponseEntity<Void> reset() {
        resetService.reset();
        return ResponseEntity.noContent().build();
    }

    /** Token ya expirado del usuario, para probar AUTH_TOKEN_EXPIRED sin esperar el TTL. */
    @PostMapping("/tokens/expired")
    public ResponseEntity<ExpiredTokenResponse> expiredToken(@Valid @RequestBody ExpiredTokenRequest request) {
        return ResponseEntity.ok(expiredTokenFactory.create(request.email()));
    }
}
