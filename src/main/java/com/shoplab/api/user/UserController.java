package com.shoplab.api.user;

import com.shoplab.api.config.OpenApiConfig;
import com.shoplab.api.security.AuthUser;
import com.shoplab.api.user.dto.RegisterUserRequest;
import com.shoplab.api.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registro público (POST /users; GET /users responde 405) y perfil protegido (GET /users/me).
 * Sin cabecera Location en el registro porque no existe GET /users/{id}.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterUserRequest request) {
        UserResponse created = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Perfil del usuario del token. Ruta protegida: sin token válido la rechaza la seguridad antes de llegar aquí.
     * Al service solo se le pasa el id (§3.2).
     */
    @GetMapping("/me")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)   // candado en Swagger UI
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal AuthUser user) {
        return ResponseEntity.ok(userService.findCurrentUser(user.id()));
    }
}
