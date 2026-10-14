package com.shoplab.api.auth;

import com.shoplab.api.auth.dto.LoginRequest;
import com.shoplab.api.auth.dto.LoginResponse;
import com.shoplab.api.common.exception.BusinessException;
import com.shoplab.api.common.exception.ErrorCode;
import com.shoplab.api.security.JwtProperties;
import com.shoplab.api.security.JwtService;
import com.shoplab.api.user.User;
import com.shoplab.api.user.UserRepository;
import com.shoplab.api.user.UserStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Usa UserRepository y no UserService (§3.3): así UserService no tiene que exponer el passwordHash.
 */
@Service
@Transactional(readOnly = true)   // el login solo lee
public class AuthServiceImpl implements AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    // Mismas fronteras que el registro: hasta 20 caracteres ASCII visibles (0x21-0x7E).
    // Una contraseña fuera de esto no puede ser correcta, y con más de 72 bytes BCrypt lanzaría una excepción (→ 500).
    private static final int MAX_PASSWORD_LENGTH = 20;
    private static final String VISIBLE_ASCII = "[\\x21-\\x7E]+";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    public AuthServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder,
                           JwtService jwtService, JwtProperties jwtProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        // 1) Credenciales: el mismo 401 si el email no existe o la contraseña no coincide (§8.3)
        User user = userRepository.findByEmail(request.email())
                .filter(u -> hasValidPasswordFormat(request.password()))
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        // 2) Solo con credenciales correctas se revela que la cuenta está bloqueada
        if (user.getStatus() == UserStatus.LOCKED) {
            throw new BusinessException(ErrorCode.USER_LOCKED);
        }

        String token = jwtService.generateToken(user.getId(), user.getEmail());
        return new LoginResponse(token, TOKEN_TYPE, jwtProperties.ttl().toSeconds());
    }

    private static boolean hasValidPasswordFormat(String password) {
        return password.length() <= MAX_PASSWORD_LENGTH && password.matches(VISIBLE_ASCII);
    }
}
