package com.shoplab.api.security;

import com.shoplab.api.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El filtro nunca responde: solo autentica o deja el motivo en un atributo (design D1).
 */
class JwtAuthenticationFilterTest {

    JwtService jwtService;
    JwtAuthenticationFilter filter;
    MockHttpServletRequest request;
    MockHttpServletResponse response;
    FilterChain chain;

    @BeforeEach
    void setUp() {
        jwtService = mock(JwtService.class);
        filter = new JwtAuthenticationFilter(jwtService);
        request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        response = new MockHttpServletResponse();
        chain = mock(FilterChain.class);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    void runFilter() throws Exception {
        filter.doFilter(request, response, chain);
        // Siempre deja seguir: la decisión de rechazar es de Spring Security
        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    Object authError() {
        return request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
    }

    @Test
    void withoutHeaderNothingHappens() throws Exception {
        runFilter();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(authError()).isNull();
        verify(jwtService, never()).parse(anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Basic xyz", "BearerXYZ", "bearer abc"})
    void otherSchemeIsTreatedAsMissing(String header) throws Exception {
        request.addHeader("Authorization", header);

        runFilter();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(authError()).isNull();
        verify(jwtService, never()).parse(anyString());
    }

    @Test
    void basicSchemeIsTreatedAsMissing() throws Exception {
        request.addHeader("Authorization", "Basic xyz");

        runFilter();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(authError()).isNull();
        verify(jwtService, never()).parse(anyString());
    }

    @ParameterizedTest
    // "Bearer" sin espacio: así llega "Bearer " por HTTP real, con el espacio final recortado
    @ValueSource(strings = {"Bearer ", "Bearer    ", "Bearer"})
    void bearerWithoutTokenIsMalformed(String header) throws Exception {
        request.addHeader("Authorization", header);

        runFilter();

        assertThat(authError()).isEqualTo(ErrorCode.AUTH_TOKEN_MALFORMED);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtService, never()).parse(anyString());
    }

    @ParameterizedTest
    @EnumSource(value = ErrorCode.class, names = {"AUTH_TOKEN_MALFORMED", "AUTH_TOKEN_INVALID", "AUTH_TOKEN_EXPIRED"})
    void rejectedTokenLeavesItsCodeInTheRequest(ErrorCode code) throws Exception {
        request.addHeader("Authorization", "Bearer bad-token");
        when(jwtService.parse("bad-token")).thenThrow(new InvalidTokenException(code));

        runFilter();

        assertThat(authError()).isEqualTo(code);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void validTokenAuthenticatesTheUser() throws Exception {
        request.addHeader("Authorization", "Bearer good-token");
        when(jwtService.parse("good-token")).thenReturn(new AuthUser(1L, "standard@shoplab.dev"));

        runFilter();

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getPrincipal()).isEqualTo(new AuthUser(1L, "standard@shoplab.dev"));
        assertThat(authError()).isNull();
    }
}
