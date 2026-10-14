package com.shoplab.api.security;

import com.shoplab.api.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Lee "Authorization: Bearer <token>" y, si el token es válido, autentica la petición con un AuthUser.
 * NUNCA responde por sí mismo (design D1): si el token falla, guarda el motivo en un atributo y deja seguir.
 * Spring Security decide después si la ruta lo exige; si es así, JwtAuthenticationEntryPoint escribe el 401.
 * No es @Component a propósito (design D3): lo crea SecurityConfig para que no se registre dos veces.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Atributo del request con el ErrorCode del rechazo. Lo lee JwtAuthenticationEntryPoint. */
    public static final String AUTH_ERROR_ATTRIBUTE = "auth.error";

    private static final String BEARER = "Bearer";
    private static final String BEARER_PREFIX = BEARER + " ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        // Sin cabecera u otro esquema (Basic…): no hay token. Si la ruta lo exige → AUTH_TOKEN_MISSING.
        // "Bearer" a secas también cuenta como Bearer vacío: por HTTP real, "Bearer " llega sin el espacio final
        // porque el cliente o el servidor recortan los espacios de la cabecera.
        if (header != null && (header.equals(BEARER) || header.startsWith(BEARER_PREFIX))) {
            String token = header.substring(BEARER.length()).trim();
            if (token.isEmpty()) {
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, ErrorCode.AUTH_TOKEN_MALFORMED);
            } else {
                authenticate(token, request);
            }
        }

        chain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request) {
        try {
            AuthUser user = jwtService.parse(token);
            // Sin roles: la lista de authorities va vacía. Con este constructor la autenticación queda "authenticated"
            var authentication = new UsernamePasswordAuthenticationToken(user, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (InvalidTokenException e) {
            request.setAttribute(AUTH_ERROR_ATTRIBUTE, e.getErrorCode());
        }
    }
}
