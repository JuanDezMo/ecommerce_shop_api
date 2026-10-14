package com.shoplab.fixtures;

import com.shoplab.api.common.validation.ValidationPatterns;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO de prueba con un campo de cada tipo que usan los DTOs reales: texto, email, contraseña,
 * país, enum, fecha, booleano y una lista anidada. Los mensajes siguen el catálogo de §9.2.
 */
public record ErrorHandlingTestRequest(
        @NotBlank(message = "must not be blank")
        @Email(message = "must be a well-formed email address")
        String email,

        @NotBlank(message = "must not be blank")
        @Size(min = 8, max = 20, message = "size must be between {min} and {max}")
        @Pattern(regexp = ValidationPatterns.PASSWORD,
                 message = "must contain at least one uppercase letter, one lowercase letter, one number and one special character, without spaces")
        String password,

        @NotBlank(message = "must not be blank")
        @Pattern(regexp = ValidationPatterns.COUNTRY, message = "must be one of: US, CO, MX, ES, AR, CL, PE")
        String country,

        @NotNull(message = "must not be null")
        TestPlan plan,

        LocalDate birthDate,

        Boolean termsAccepted,

        @NotNull(message = "must not be null")
        List<@NotNull(message = "must not be null") @Valid Item> items) {

    public enum TestPlan { BASIC, PRO }

    public record Item(
            @NotNull(message = "must not be null")
            Long productId,

            @NotNull(message = "must not be null")
            @Min(value = 1, message = "must be between 1 and 99")
            @Max(value = 99, message = "must be between 1 and 99")
            Integer quantity) {
    }
}
