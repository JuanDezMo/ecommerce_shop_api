package com.shoplab.api.common.validation;

/**
 * Expresiones regulares compartidas por las anotaciones @Pattern de los DTOs.
 * Son constantes para poder usarlas dentro de las anotaciones.
 */
public final class ValidationPatterns {

    // Al menos una minúscula, una mayúscula, un dígito y un especial; solo ASCII visible (0x21-0x7E)
    public static final String PASSWORD = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9])[\\x21-\\x7E]+$";

    // Países soportados (ISO 3166 alfa-2). @Pattern exige que coincida el texto completo
    public static final String COUNTRY = "US|CO|MX|ES|AR|CL|PE";

    public static final String POSTAL_CODE = "^[A-Za-z0-9 -]{3,10}$";

    private ValidationPatterns() {
    }
}
