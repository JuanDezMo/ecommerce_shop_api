package com.shoplab.api.security;

import com.shoplab.api.product.ProductController;
import com.shoplab.api.product.ProductService;
import com.shoplab.api.support.ContractAssertions;
import com.shoplab.api.support.TestTokens;
import com.shoplab.api.support.WebMvcSecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.stream.Stream;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tabla de rutas de §7.1 y escenarios de §8.2 contra la cadena de seguridad real.
 * /api/v1/foo es una ruta protegida que no existe: sirve para probar la seguridad sin depender de un controller.
 */
@WebMvcTest(ProductController.class)
@Import(WebMvcSecurityConfig.class)
class SecurityRulesTest {

    static final String PROTECTED = "/api/v1/foo";
    static final String EMAIL = "standard@shoplab.dev";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ProductService productService;

    @Test
    void catalogIsPublic() throws Exception {
        when(productService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void brokenTokenIsIgnoredOnPublicRoutes() throws Exception {
        when(productService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/products").header("Authorization", "Bearer abc.def"))
                .andExpect(status().isOk());
    }

    @Test
    void unsupportedMethodOnPublicRouteIs405NotUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/v1/products/1"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void unknownRouteInsidePublicRouteIs404() throws Exception {
        mockMvc.perform(get("/api/v1/products/1/reviews"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    static Stream<Arguments> rejectedAuthorizations() {
        return Stream.of(
                Arguments.of("sin cabecera", null, "AUTH_TOKEN_MISSING"),
                Arguments.of("otro esquema", "Basic xyz", "AUTH_TOKEN_MISSING"),
                Arguments.of("Bearer vacío", "Bearer ", "AUTH_TOKEN_MALFORMED"),
                Arguments.of("no es un JWT", "Bearer abc.def", "AUTH_TOKEN_MALFORMED"),
                Arguments.of("otro secreto", TestTokens.bearer(TestTokens.signedWithOtherSecret(1L, EMAIL)), "AUTH_TOKEN_INVALID"),
                Arguments.of("payload alterado", TestTokens.bearer(TestTokens.tamperedPayload(1L, EMAIL)), "AUTH_TOKEN_INVALID"),
                Arguments.of("sin firma", TestTokens.bearer(TestTokens.unsigned(1L, EMAIL)), "AUTH_TOKEN_INVALID"),
                Arguments.of("expirado", TestTokens.bearer(TestTokens.expired(1L, EMAIL)), "AUTH_TOKEN_EXPIRED"),
                Arguments.of("expirado y alterado", TestTokens.bearer(TestTokens.expiredAndTampered(1L, EMAIL)), "AUTH_TOKEN_INVALID"));
    }

    @ParameterizedTest(name = "{0} → {2}")
    @MethodSource("rejectedAuthorizations")
    void protectedRouteRejectsWithTheReason(String scenario, String authorization, String expectedCode) throws Exception {
        var request = get(PROTECTED);
        if (authorization != null) {
            request.header("Authorization", authorization);
        }

        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.path").value(PROTECTED))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn();
        ContractAssertions.assertMatchesSchema(result.getResponse().getContentAsString(), "error-response");
    }

    @Test
    void validTokenReachesRoutingAndUnknownRouteIs404() throws Exception {
        mockMvc.perform(get(PROTECTED).header("Authorization", TestTokens.bearer(TestTokens.valid(1L, EMAIL))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }
}
