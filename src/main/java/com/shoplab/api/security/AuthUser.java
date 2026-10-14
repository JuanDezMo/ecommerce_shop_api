package com.shoplab.api.security;

/**
 * Usuario autenticado, sacado del token (claims sub y email). Es el principal de Spring Security:
 * los controllers lo reciben con @AuthenticationPrincipal y le pasan solo el id al service (§3.2).
 */
public record AuthUser(Long id, String email) {
}
