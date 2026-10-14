package com.shoplab.api.auth;

import com.shoplab.api.auth.dto.LoginRequest;
import com.shoplab.api.auth.dto.LoginResponse;

/**
 * Inicio de sesión. Devuelve el token o lanza BusinessException (401 INVALID_CREDENTIALS, 423 USER_LOCKED).
 */
public interface AuthService {

    LoginResponse login(LoginRequest request);
}
