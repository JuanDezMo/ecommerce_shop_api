package com.shoplab.api.common.exception;

import com.shoplab.api.security.SecurityConfig;
import com.shoplab.api.support.ContractAssertions;
import com.shoplab.fixtures.ErrorHandlingTestController;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Escenarios de specs/error-handling probados contra un controller de prueba (design D4).
 * El slice se limita a ese controller: sin esto cargaría también los controllers reales
 * (ProductController…), que necesitan services que este test no declara.
 */
@WebMvcTest(controllers = ErrorHandlingTestController.class)
@Import({SecurityConfig.class, ErrorHandlingTestController.class})
class GlobalExceptionHandlerTest {

    private static final String BODY_URL = "/test-errors/body";

    /** Body válido: cada test cambia solo lo que quiere romper. */
    static final String VALID_BODY = """
            {
              "email": "standard@shoplab.dev",
              "password": "Secret_123",
              "country": "CO",
              "plan": "BASIC",
              "birthDate": "1990-05-20",
              "termsAccepted": true,
              "items": [ { "productId": 1, "quantity": 2 }, { "productId": 4, "quantity": 1 } ]
            }
            """;

    @Autowired
    MockMvc mockMvc;

    ResultActions postBody(String json) throws Exception {
        return mockMvc.perform(post(BODY_URL).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** Cada respuesta de error debe cumplir el contrato JSON. */
    static void assertErrorContract(MvcResult result) throws Exception {
        ContractAssertions.assertMatchesSchema(result.getResponse().getContentAsString(), "error-response");
    }

    @Test
    void validBodyIsAccepted() throws Exception {
        postBody(VALID_BODY).andExpect(status().isOk());
    }

    @Nested
    class UniformFormat {

        @Test
        void businessErrorHasAllFieldsAndEmptyFieldErrors() throws Exception {
            MvcResult result = mockMvc.perform(get("/test-errors/product-not-found"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("Product 99 not found"))
                    .andExpect(jsonPath("$.path").value("/test-errors/product-not-found"))
                    .andExpect(jsonPath("$.timestamp").isString())
                    .andExpect(jsonPath("$.fieldErrors", hasSize(0)))
                    .andReturn();
            assertErrorContract(result);
        }

        @Test
        void fixedCatalogMessage() throws Exception {
            MvcResult result = mockMvc.perform(get("/test-errors/locked"))
                    .andExpect(status().isLocked())
                    .andExpect(jsonPath("$.code").value("USER_LOCKED"))
                    .andExpect(jsonPath("$.message").value("User is locked"))
                    .andReturn();
            assertErrorContract(result);
        }
    }

    @Nested
    class FieldValidation {

        @Test
        void severalInvalidFieldsAreSortedByField() throws Exception {
            // sin "country" (null): solo falla @NotBlank; con "" fallarían @NotBlank y @Pattern
            String body = VALID_BODY.replace("standard@shoplab.dev", "bad").replace("\"country\": \"CO\",", "");

            MvcResult result = postBody(body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message").value("Request has invalid fields"))
                    .andExpect(jsonPath("$.fieldErrors", hasSize(2)))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("country"))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value("must not be blank"))
                    .andExpect(jsonPath("$.fieldErrors[1].field").value("email"))
                    .andExpect(jsonPath("$.fieldErrors[1].message").value("must be a well-formed email address"))
                    .andReturn();
            assertErrorContract(result);
        }

        @Test
        void oneFieldBreakingTwoRulesIsSortedByMessage() throws Exception {
            String body = VALID_BODY.replace("Secret_123", "abc");

            postBody(body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors", hasSize(2)))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("password"))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value(
                            "must contain at least one uppercase letter, one lowercase letter, one number and one special character, without spaces"))
                    .andExpect(jsonPath("$.fieldErrors[1].field").value("password"))
                    .andExpect(jsonPath("$.fieldErrors[1].message").value("size must be between 8 and 20"));
        }

        @Test
        void nestedFieldInListUsesIndexedPath() throws Exception {
            String body = VALID_BODY.replace("{ \"productId\": 4, \"quantity\": 1 }", "{ \"productId\": 4, \"quantity\": 0 }");

            postBody(body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("items[1].quantity"))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value("must be between 1 and 99"));
        }
    }

    @Test
    void blankFieldReportsEveryBrokenRule() throws Exception {
        postBody(VALID_BODY.replace("\"CO\"", "\"\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors", hasSize(2)))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("must be one of: US, CO, MX, ES, AR, CL, PE"))
                .andExpect(jsonPath("$.fieldErrors[1].message").value("must not be blank"));
    }

    @Nested
    class Routing {

        @Test
        void unknownRouteIs404() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/v1/does-not-exist"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("Resource not found"))
                    .andExpect(jsonPath("$.path").value("/api/v1/does-not-exist"))
                    .andReturn();
            assertErrorContract(result);
        }

        @Test
        void unsupportedMethodIs405() throws Exception {
            MvcResult result = mockMvc.perform(delete("/test-errors/items/1"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                    .andExpect(jsonPath("$.message").value("HTTP method not allowed"))
                    .andReturn();
            assertErrorContract(result);
        }

        @Test
        void plainTextBodyIs415() throws Exception {
            MvcResult result = mockMvc.perform(post(BODY_URL).contentType(MediaType.TEXT_PLAIN).content("hello"))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
                    .andExpect(jsonPath("$.message").value("Content-Type must be application/json"))
                    .andReturn();
            assertErrorContract(result);
        }
    }

    @Nested
    class MalformedBody {

        void expectMalformed(ResultActions actions) throws Exception {
            MvcResult result = actions
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message").value("Malformed JSON request"))
                    .andExpect(jsonPath("$.fieldErrors", hasSize(0)))
                    .andReturn();
            assertErrorContract(result);
        }

        @Test
        void truncatedJson() throws Exception {
            expectMalformed(postBody("{\"items\": ["));
        }

        @Test
        void missingBody() throws Exception {
            expectMalformed(mockMvc.perform(post(BODY_URL).contentType(MediaType.APPLICATION_JSON)));
        }

        @Test
        void emptyBody() throws Exception {
            expectMalformed(postBody(""));
        }

        @Test
        void arrayInsteadOfObject() throws Exception {
            expectMalformed(postBody("[]"));
        }
    }

    @Nested
    class PathParameter {

        @Test
        void nonNumericIdIs400WithParameterName() throws Exception {
            MvcResult result = mockMvc.perform(get("/test-errors/items/abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message").value("Request has invalid fields"))
                    .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("id"))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value("must be an integer"))
                    .andReturn();
            assertErrorContract(result);
        }
    }

    @Test
    void unknownFieldsAreIgnored() throws Exception {
        postBody(VALID_BODY.replace("\"plan\": \"BASIC\",", "\"plan\": \"BASIC\", \"foo\": 1,"))
                .andExpect(status().isOk());
    }

    @Nested
    class TypeErrors {

        void expectSingleFieldError(String body, String field, String message) throws Exception {
            MvcResult result = postBody(body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message").value("Request has invalid fields"))
                    .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value(field))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value(message))
                    .andReturn();
            assertErrorContract(result);
        }

        @Test
        void invalidEnum() throws Exception {
            expectSingleFieldError(VALID_BODY.replace("\"BASIC\"", "\"GOLD\""), "plan", "must be one of: BASIC, PRO");
        }

        @Test
        void numberSentAsText() throws Exception {
            expectSingleFieldError(VALID_BODY.replace("\"quantity\": 2", "\"quantity\": \"2\""),
                    "items[0].quantity", "must be an integer");
        }

        @Test
        void decimalWhereIntegerIsExpected() throws Exception {
            expectSingleFieldError(VALID_BODY.replace("\"quantity\": 2", "\"quantity\": 1.5"),
                    "items[0].quantity", "must be an integer");
        }

        @Test
        void booleanSentAsText() throws Exception {
            expectSingleFieldError(VALID_BODY.replace("\"termsAccepted\": true", "\"termsAccepted\": \"true\""),
                    "termsAccepted", "must be true or false");
        }

        @Test
        void dateWithWrongFormat() throws Exception {
            expectSingleFieldError(VALID_BODY.replace("1990-05-20", "20-05-1990"),
                    "birthDate", "must be a valid date (yyyy-MM-dd)");
        }
    }

    @Nested
    class UnexpectedError {

        @Test
        void unexpectedErrorIs500WithoutInternalDetails() throws Exception {
            MvcResult result = mockMvc.perform(get("/test-errors/boom"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.message").value("Unexpected error"))
                    .andExpect(content().string(not(containsString("s3cr3t"))))
                    .andExpect(content().string(not(containsString("IllegalStateException"))))
                    .andExpect(content().string(not(containsString("com.shoplab"))))
                    .andReturn();
            assertErrorContract(result);
        }
    }
}
