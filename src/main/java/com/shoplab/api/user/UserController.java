package com.shoplab.api.user;

import com.shoplab.api.user.dto.RegisterUserRequest;
import com.shoplab.api.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registro público de usuarios. Solo POST: GET /users responde 405.
 * Sin cabecera Location porque no existe GET /users/{id} (design D1).
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
}
