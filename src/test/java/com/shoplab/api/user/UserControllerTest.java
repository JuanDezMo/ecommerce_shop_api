package com.shoplab.api.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shoplab.api.common.exception.BusinessException;
import com.shoplab.api.common.exception.ErrorCode;
import com.shoplab.api.support.ContractAssertions;
import com.shoplab.api.support.TestTokens;
import com.shoplab.api.support.WebMvcSecurityConfig;
import com.shoplab.api.user.dto.RegisterUserRequest;
import com.shoplab.api.user.dto.UserResponse;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Capa web del registro con el service mockeado (design D5).
 */
@WebMvcTest(UserController.class)
@Import(WebMvcSecurityConfig.class)   // seguridad real (design D7 de auth-jwt-filter)
class UserControllerTest {

    static final String URL = "/api/v1/users";

    /** Body válido sin phone ni newsletter: cada test cambia solo lo que quiere romper. */
    static final String VALID_BODY = """
            {
              "firstName": "Ana",
              "lastName": "Gomez",
              "email": "  Ana@Example.com ",
              "password": "Secret_123",
              "birthDate": "1990-05-20",
              "country": "CO",
              "plan": "BASIC",
              "experienceLevel": 3,
              "termsAccepted": true
            }
            """;

    static final Instant NOW = Instant.parse("2026-10-11T15:04:05Z");
    static final UserResponse CREATED = new UserResponse(4L, "ana@example.com", "Ana", "Gomez", null,
            LocalDate.of(1990, 5, 20), "CO", Plan.BASIC, 3, false, UserStatus.ACTIVE, NOW, NOW);

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserService userService;

    ResultActions postJson(String json) throws Exception {
        return mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    static final ObjectMapper MAPPER = new ObjectMapper();

    /** VALID_BODY con un campo reemplazado por un valor JSON escrito tal cual ("\"bad\"", "0", "null"…). */
    static String validBodyWith(String field, String rawJsonValue) throws Exception {
        ObjectNode node = (ObjectNode) MAPPER.readTree(VALID_BODY);
        node.set(field, MAPPER.readTree(rawJsonValue));
        return MAPPER.writeValueAsString(node);
    }

    /** 400 VALIDATION_ERROR con contrato de error y sin llamar al service. */
    ResultActions expectValidationError(String json) throws Exception {
        ResultActions actions = postJson(json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request has invalid fields"));
        ContractAssertions.assertMatchesSchema(body(actions.andReturn()), "error-response");
        verifyNoInteractions(userService);
        return actions;
    }

    @Test
    void validBodyIs201WithTheCreatedUser() throws Exception {
        when(userService.register(any())).thenReturn(CREATED);

        MvcResult result = postJson(VALID_BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(4))
                .andExpect(jsonPath("$.email").value("ana@example.com"))
                .andExpect(jsonPath("$.phone").isEmpty())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-11T15:04:05Z"))
                .andReturn();

        ContractAssertions.assertMatchesSchema(body(result), "user-response");
        assertThat(body(result)).doesNotContain("password");
    }

    @Test
    void serviceReceivesNormalizedEmailAndNewsletterFalse() throws Exception {
        when(userService.register(any())).thenReturn(CREATED);

        postJson(VALID_BODY).andExpect(status().isCreated());

        ArgumentCaptor<RegisterUserRequest> captor = ArgumentCaptor.forClass(RegisterUserRequest.class);
        verify(userService).register(captor.capture());
        assertThat(captor.getValue().email()).isEqualTo("ana@example.com");
        assertThat(captor.getValue().newsletter()).isFalse();
        assertThat(captor.getValue().phone()).isNull();
    }

    @Nested
    class FieldValidation {

        static final String PASSWORD_POLICY =
                "must contain at least one uppercase letter, one lowercase letter, one number and one special character, without spaces";

        // valor JSON | campo | mensaje esperado (un solo error)
        @ParameterizedTest(name = "{0} = {1} → {2}")
        @CsvSource(delimiter = '|', textBlock = """
                firstName       | "A"                                    | size must be between 2 and 50
                firstName       | "   "                                  | must not be blank
                lastName        | "GomezGomezGomezGomezGomezGomezGomezGomezGomezGomezG" | size must be between 2 and 50
                email           | "bad"                                  | must be a well-formed email address
                email           | "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa@bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb.com" | size must be at most 100
                password        | "secret_123"                           | %s
                password        | "Secret 123"                           | %s
                password        | "Secret_123Secret_1234"                | size must be between 8 and 20
                phone           | "300-123"                              | must contain only digits (7 to 15)
                phone           | ""                                     | must contain only digits (7 to 15)
                birthDate       | "2999-01-01"                           | must be a date in the past
                country         | "BR"                                   | must be one of: US, CO, MX, ES, AR, CL, PE
                country         | "co"                                   | must be one of: US, CO, MX, ES, AR, CL, PE
                plan            | null                                   | must not be null
                experienceLevel | 0                                      | must be between 1 and 5
                experienceLevel | 6                                      | must be between 1 and 5
                termsAccepted   | null                                   | must not be null
                """)
        void invalidFieldIs400WithItsMessage(String field, String rawJsonValue, String message) throws Exception {
            String expected = message.equals("%s") ? PASSWORD_POLICY : message;

            expectValidationError(validBodyWith(field, rawJsonValue))
                    .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value(field))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value(expected));
        }

        @Test
        void emptyBodyReportsTheNineRequiredFieldsSorted() throws Exception {
            expectValidationError("{}")
                    .andExpect(jsonPath("$.fieldErrors", hasSize(9)))
                    .andExpect(jsonPath("$.fieldErrors[*].field").value(org.hamcrest.Matchers.contains(
                            "birthDate", "country", "email", "experienceLevel", "firstName",
                            "lastName", "password", "plan", "termsAccepted")));
        }

        @Test
        void shortWeakPasswordReportsBothRulesSortedByMessage() throws Exception {
            expectValidationError(validBodyWith("password", "\"abc\""))
                    .andExpect(jsonPath("$.fieldErrors", hasSize(2)))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value(PASSWORD_POLICY))
                    .andExpect(jsonPath("$.fieldErrors[1].message").value("size must be between 8 and 20"));
        }

        @Test
        void emptyCountryReportsBothRules() throws Exception {
            expectValidationError(validBodyWith("country", "\"\""))
                    .andExpect(jsonPath("$.fieldErrors", hasSize(2)))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value("must be one of: US, CO, MX, ES, AR, CL, PE"))
                    .andExpect(jsonPath("$.fieldErrors[1].message").value("must not be blank"));
        }
    }

    @Nested
    class TypeErrors {

        @ParameterizedTest(name = "{0} = {1} → {2}")
        @CsvSource(delimiter = '|', textBlock = """
                plan            | "GOLD"         | must be one of: BASIC, PRO
                experienceLevel | "3"            | must be an integer
                experienceLevel | 3.5            | must be an integer
                termsAccepted   | "true"         | must be true or false
                birthDate       | "20-05-1990"   | must be a valid date (yyyy-MM-dd)
                """)
        void wrongTypeIs400WithOneFieldError(String field, String rawJsonValue, String message) throws Exception {
            expectValidationError(validBodyWith(field, rawJsonValue))
                    .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value(field))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value(message));
        }
    }

    @Nested
    class BusinessErrors {

        @ParameterizedTest
        @EnumSource(value = ErrorCode.class, names = {"UNDERAGE_USER", "TERMS_NOT_ACCEPTED", "EMAIL_ALREADY_EXISTS"})
        void businessErrorUsesItsStatusAndMessage(ErrorCode errorCode) throws Exception {
            when(userService.register(any())).thenThrow(new BusinessException(errorCode));

            MvcResult result = postJson(VALID_BODY)
                    .andExpect(status().is(errorCode.getStatus().value()))
                    .andExpect(jsonPath("$.code").value(errorCode.name()))
                    .andExpect(jsonPath("$.message").value(errorCode.getMessage()))
                    .andExpect(jsonPath("$.fieldErrors", hasSize(0)))
                    .andReturn();
            ContractAssertions.assertMatchesSchema(body(result), "error-response");
        }
    }

    @Test
    void listingUsersIs405() throws Exception {
        MvcResult result = mockMvc.perform(get(URL))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andReturn();
        ContractAssertions.assertMatchesSchema(body(result), "error-response");
    }

    @Nested
    class CurrentUser {

        static final String ME = "/api/v1/users/me";
        final String token = TestTokens.bearer(TestTokens.valid(4L, "ana@example.com"));

        @Test
        void validTokenReturnsTheProfileOfItsUser() throws Exception {
            when(userService.findCurrentUser(4L)).thenReturn(CREATED);

            MvcResult result = mockMvc.perform(get(ME).header("Authorization", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(4))
                    .andExpect(jsonPath("$.email").value("ana@example.com"))
                    .andReturn();

            ContractAssertions.assertMatchesSchema(body(result), "user-response");
            assertThat(body(result)).doesNotContain("password");
            // El service recibe solo el id del token, nunca el AuthUser (§3.2)
            verify(userService).findCurrentUser(4L);
        }

        @Test
        void deletedUserIs401TokenInvalid() throws Exception {
            when(userService.findCurrentUser(4L)).thenThrow(new BusinessException(ErrorCode.AUTH_TOKEN_INVALID));

            MvcResult result = mockMvc.perform(get(ME).header("Authorization", token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"))
                    .andExpect(jsonPath("$.message").value("Authentication token is invalid"))
                    .andReturn();
            ContractAssertions.assertMatchesSchema(body(result), "error-response");
        }

        @Test
        void withoutTokenIs401MissingAndTheServiceIsNotCalled() throws Exception {
            mockMvc.perform(get(ME))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_TOKEN_MISSING"));
            verifyNoInteractions(userService);
        }

        @Test
        void putWithTokenIs405() throws Exception {
            mockMvc.perform(put(ME).header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        }
    }
}
