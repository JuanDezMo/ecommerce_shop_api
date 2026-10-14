package com.shoplab.api.user;

import com.shoplab.api.common.exception.BusinessException;
import com.shoplab.api.common.exception.ErrorCode;
import com.shoplab.api.user.dto.RegisterUserRequest;
import com.shoplab.api.user.dto.UserResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;

@Service
@Transactional   // §11, regla 2
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UserServiceImpl(UserRepository userRepository, UserMapper userMapper,
                           PasswordEncoder passwordEncoder, Clock clock) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    public UserResponse register(RegisterUserRequest request) {
        // Reglas de negocio en el orden de §6.4: edad, términos, email duplicado
        LocalDate today = LocalDate.now(clock);   // UTC (§6.5)
        if (Period.between(request.birthDate(), today).getYears() < 18) {
            throw new BusinessException(ErrorCode.UNDERAGE_USER);
        }
        if (!request.termsAccepted()) {
            throw new BusinessException(ErrorCode.TERMS_NOT_ACCEPTED);
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        // Un solo instante para las dos fechas, truncado a microsegundos como lo guarda PostgreSQL (design D3)
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);

        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPhone(request.phone());
        user.setBirthDate(request.birthDate());
        user.setCountry(request.country());
        user.setPlan(request.plan());
        user.setExperienceLevel(request.experienceLevel());
        user.setNewsletter(request.newsletter());
        user.setStatus(UserStatus.ACTIVE);
        user.setTermsAcceptedAt(now);
        user.setCreatedAt(now);

        try {
            // saveAndFlush: el INSERT se ejecuta aquí dentro, así el catch puede atrapar el UNIQUE
            User saved = userRepository.saveAndFlush(user);
            return userMapper.toResponse(saved);
        } catch (DataIntegrityViolationException e) {
            // Otro registro con el mismo email ganó la carrera entre el exists y el insert.
            // La única UNIQUE de users es email, así que no hay otra causa posible.
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse findCurrentUser(Long userId) {
        // El token puede ser válido aunque su usuario ya no exista (por ejemplo, tras /test/reset): 401
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_TOKEN_INVALID));
        return userMapper.toResponse(user);
    }
}
