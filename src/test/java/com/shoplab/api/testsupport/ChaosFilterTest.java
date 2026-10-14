package com.shoplab.api.testsupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.shoplab.api.support.ContractAssertions;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * X-Delay y X-Force-Error (design D4). El retraso se registra sin dormir: así el recorte a 10000 ms
 * se prueba sin esperar 10 segundos.
 */
class ChaosFilterTest {

    static final ObjectMapper OBJECT_MAPPER = Jackson2ObjectMapperBuilder.json()
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)   // igual que Spring Boot
            .build();

    /** Subclase de prueba: anota los milisegundos en vez de dormir. */
    static class RecordingChaosFilter extends ChaosFilter {
        final List<Long> sleeps = new ArrayList<>();

        RecordingChaosFilter() {
            super(OBJECT_MAPPER);
        }

        @Override
        protected void sleep(long millis) {
            sleeps.add(millis);
        }
    }

    RecordingChaosFilter filter;
    MockHttpServletRequest request;
    MockHttpServletResponse response;
    FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new RecordingChaosFilter();
        request = new MockHttpServletRequest("GET", "/api/v1/products");
        response = new MockHttpServletResponse();
        chain = mock(FilterChain.class);
    }

    // ---------- reglas de las cabeceras (métodos puros) ----------

    @ParameterizedTest(name = "X-Delay: {0} → {1} ms")
    @CsvSource({"500, 500", "0, 0", "10000, 10000", "50000, 10000", "abc, 0", "-5, 0", "1.5, 0", "'', 0"})
    void delayMillisRules(String header, long expected) {
        assertThat(ChaosFilter.delayMillis(header)).isEqualTo(expected);
    }

    @Test
    void missingDelayIsZero() {
        assertThat(ChaosFilter.delayMillis(null)).isZero();
    }

    @ParameterizedTest(name = "X-Force-Error: {0} → {1}")
    @CsvSource({"400, 400", "418, 418", "500, 500", "599, 599"})
    void forcedStatusInRange(String header, int expected) {
        assertThat(ChaosFilter.forcedStatus(header)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "X-Force-Error: {0} → ignorado")
    @ValueSource(strings = {"200", "399", "600", "700", "abc", "5.0", ""})
    @NullSource
    void forcedStatusOutOfRangeIsIgnored(String header) {
        assertThat(ChaosFilter.forcedStatus(header)).isNull();
    }

    // ---------- comportamiento del filtro ----------

    @Test
    void withoutHeadersTheRequestContinues() throws Exception {
        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(filter.sleeps).isEmpty();
    }

    @Test
    void delayIsAppliedAndClampedThenTheRequestContinues() throws Exception {
        request.addHeader("X-Delay", "50000");

        filter.doFilter(request, response, chain);

        assertThat(filter.sleeps).containsExactly(10000L);
        verify(chain).doFilter(request, response);
    }

    @Test
    void forcedErrorWritesTheErrorAndStopsTheRequest() throws Exception {
        request.addHeader("X-Force-Error", "503");

        filter.doFilter(request, response, chain);

        // La petición no llega al controller: no puede crear ni modificar nada
        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentType()).startsWith("application/json");
        String body = response.getContentAsString();
        ContractAssertions.assertMatchesSchema(body, "error-response");
        JsonNode json = OBJECT_MAPPER.readTree(body);
        assertThat(json.get("status").asInt()).isEqualTo(503);
        assertThat(json.get("code").asText()).isEqualTo("FORCED_ERROR");
        assertThat(json.get("message").asText()).isEqualTo("Forced error for testing");
        assertThat(json.get("path").asText()).isEqualTo("/api/v1/products");
        assertThat(json.get("fieldErrors")).isEmpty();
    }

    @Test
    void delayAndErrorTogetherWaitFirstThenFail() throws Exception {
        request.addHeader("X-Delay", "300");
        request.addHeader("X-Force-Error", "502");

        filter.doFilter(request, response, chain);

        assertThat(filter.sleeps).containsExactly(300L);
        assertThat(response.getStatus()).isEqualTo(502);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void ignoredErrorValueLetsTheRequestContinue() throws Exception {
        request.addHeader("X-Force-Error", "200");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
