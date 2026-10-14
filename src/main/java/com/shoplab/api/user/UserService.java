package com.shoplab.api.user;

import com.shoplab.api.user.dto.RegisterUserRequest;
import com.shoplab.api.user.dto.UserResponse;

/**
 * Registro de usuarios. Devuelve el DTO o lanza BusinessException; nunca Optional ni null.
 */
public interface UserService {

    /** Crea un usuario ACTIVE. Reglas de negocio en orden: edad, términos, email duplicado (§6.4). */
    UserResponse register(RegisterUserRequest request);
}
