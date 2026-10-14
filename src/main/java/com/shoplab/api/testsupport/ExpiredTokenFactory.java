package com.shoplab.api.testsupport;

import com.shoplab.api.common.exception.BusinessException;
import com.shoplab.api.common.exception.ErrorCode;
import com.shoplab.api.security.JjwtService;
import com.shoplab.api.security.JwtProperties;
import com.shoplab.api.testsupport.dto.ExpiredTokenResponse;
import com.shoplab.api.user.User;
import com.shoplab.api.user.UserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/**
 * Emite tokens ya expirados para probar AUTH_TOKEN_EXPIRED sin esperar el TTL (§8.4).
 * Firma con el mismo JjwtService que el login, pero con un reloj en el pasado: el token es válido
 * en todo menos en la fecha, así que la API responde EXPIRED y nunca INVALID.
 * Usa UserRepository (concesión de §3.3); el controller no puede, lo prohíbe ArchitectureTest.
 */
@Component
@Profile("testing")
public class ExpiredTokenFactory {

    private static final String TOKEN_TYPE = "Bearer";
    // Emitido hace TTL + 1 minuto → venció hace 1 minuto
    private static final Duration EXTRA_PAST = Duration.ofMinutes(1);

    private final UserRepository userRepository;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public ExpiredTokenFactory(UserRepository userRepository, JwtProperties jwtProperties, Clock clock) {
        this.userRepository = userRepository;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    public ExpiredTokenResponse create(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Clock past = Clock.offset(clock, jwtProperties.ttl().plus(EXTRA_PAST).negated());
        String token = new JjwtService(jwtProperties, past).generateToken(user.getId(), user.getEmail());
        return new ExpiredTokenResponse(token, TOKEN_TYPE);
    }
}
