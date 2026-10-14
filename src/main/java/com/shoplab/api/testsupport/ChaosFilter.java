package com.shoplab.api.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoplab.api.common.exception.ErrorCode;
import com.shoplab.api.security.ErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Cabeceras de caos del perfil testing (§12):
 * - X-Delay: n      → espera n ms (máximo 10000) antes de responder. Practicar esperas en Appium/Maestro.
 * - X-Force-Error: s → responde el status s (400–599) con code FORCED_ERROR. Practicar pantallas de error.
 * Los valores inválidos se ignoran. Si llegan las dos, primero espera y luego falla.
 * Es un bean con la máxima prioridad para ejecutarse ANTES que Spring Security (§7.1, etapa 1):
 * así funciona también sin token. A diferencia de JwtAuthenticationFilter, aquí sí se quiere un filtro de servlet.
 */
@Component
@Profile("testing")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ChaosFilter extends OncePerRequestFilter {

    static final String DELAY_HEADER = "X-Delay";
    static final String FORCE_ERROR_HEADER = "X-Force-Error";
    static final long MAX_DELAY_MILLIS = 10_000;

    private final ObjectMapper objectMapper;

    public ChaosFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long delay = delayMillis(request.getHeader(DELAY_HEADER));
        if (delay > 0) {
            sleep(delay);
        }

        Integer forcedStatus = forcedStatus(request.getHeader(FORCE_ERROR_HEADER));
        if (forcedStatus != null) {
            // No se llama a chain.doFilter: la petición no llega al controller ni modifica datos
            ErrorResponseWriter.write(response, objectMapper, ErrorCode.FORCED_ERROR, forcedStatus, request);
            return;
        }

        chain.doFilter(request, response);
    }

    /** Milisegundos a esperar: 0 si falta, no es un entero o es negativo; nunca más de 10000. */
    static long delayMillis(String header) {
        try {
            long millis = Long.parseLong(header);
            return millis < 0 ? 0 : Math.min(millis, MAX_DELAY_MILLIS);
        } catch (NumberFormatException e) {   // también cubre header == null
            return 0;
        }
    }

    /** Status a forzar si es un entero entre 400 y 599; null en cualquier otro caso. */
    static Integer forcedStatus(String header) {
        try {
            int status = Integer.parseInt(header);
            return (status >= 400 && status <= 599) ? status : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** protected para que el test pueda registrar el valor sin esperar de verdad. */
    protected void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
