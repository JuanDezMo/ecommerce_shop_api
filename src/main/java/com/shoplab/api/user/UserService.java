package com.shoplab.api.user;

import com.shoplab.api.user.dto.RegisterUserRequest;
import com.shoplab.api.user.dto.UserResponse;

/**
 * Registro y perfil de usuarios. Devuelve el DTO o lanza BusinessException; nunca Optional ni null.
 */
public interface UserService {

    /** Crea un usuario ACTIVE. Reglas de negocio en orden: edad, términos, email duplicado (§6.4). */
    UserResponse register(RegisterUserRequest request);

    /**
     * Perfil del usuario autenticado. Recibe el id del token (nunca AuthUser, §3.2).
     * Si el usuario ya no existe lanza BusinessException(AUTH_TOKEN_INVALID) → 401.
     */
    UserResponse findCurrentUser(Long userId);
}
