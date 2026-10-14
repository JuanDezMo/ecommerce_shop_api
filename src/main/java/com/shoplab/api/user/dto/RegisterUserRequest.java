package com.shoplab.api.user.dto;

import com.shoplab.api.common.validation.ValidationPatterns;
import com.shoplab.api.user.Plan;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Locale;

/**
 * Body de POST /users. Reglas de §6.4 con los mensajes fijos de §9.2 (escritos a mano para que
 * no dependan del idioma del sistema operativo).
 * experienceLevel, newsletter y termsAccepted son tipos objeto: con primitivos, un campo ausente
 * llegaría como 0 / false y @NotNull nunca saltaría.
 */
public record RegisterUserRequest(
        @NotBlank(message = "must not be blank")
        @Size(min = 2, max = 50, message = "size must be between {min} and {max}")
        String firstName,

        @NotBlank(message = "must not be blank")
        @Size(min = 2, max = 50, message = "size must be between {min} and {max}")
        String lastName,

        @NotBlank(message = "must not be blank")
        @Email(message = "must be a well-formed email address")
        @Size(max = 100, message = "size must be at most {max}")
        String email,

        @NotBlank(message = "must not be blank")
        @Size(min = 8, max = 20, message = "size must be between {min} and {max}")
        @Pattern(regexp = ValidationPatterns.PASSWORD,
                message = "must contain at least one uppercase letter, one lowercase letter, one number and one special character, without spaces")
        String password,

        // Opcional: null es válido (@Pattern ignora null), pero "" no
        @Pattern(regexp = "^\\d{7,15}$", message = "must contain only digits (7 to 15)")
        String phone,

        @NotNull(message = "must not be null")
        @Past(message = "must be a date in the past")
        LocalDate birthDate,

        @NotBlank(message = "must not be blank")
        @Pattern(regexp = ValidationPatterns.COUNTRY, message = "must be one of: US, CO, MX, ES, AR, CL, PE")
        String country,

        @NotNull(message = "must not be null")
        Plan plan,

        @NotNull(message = "must not be null")
        @Min(value = 1, message = "must be between 1 and 5")
        @Max(value = 5, message = "must be between 1 and 5")
        Integer experienceLevel,

        Boolean newsletter,

        @NotNull(message = "must not be null")
        Boolean termsAccepted) {

    public RegisterUserRequest {
        // Se ejecuta al deserializar el JSON, antes de @Valid (§6.3)
        email = (email == null) ? null : email.trim().toLowerCase(Locale.ROOT);
        newsletter = (newsletter == null) ? Boolean.FALSE : newsletter;
    }
}
