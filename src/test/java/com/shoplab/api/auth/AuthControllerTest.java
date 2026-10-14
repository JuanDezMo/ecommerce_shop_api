package com.shoplab.api.auth;

import com.shoplab.api.auth.dto.LoginRequest;
import com.shoplab.api.auth.dto.LoginResponse;
import com.shoplab.api.common.exception.BusinessException;
import com.shoplab.api.common.exception.ErrorCode;
import com.shoplab.api.support.ContractAssertions;
import com.shoplab.api.support.WebMvcSecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Capa web del login con el service mockeado.
 */
@WebMvcTest(AuthController.class)
@Import(WebMvcSecurityConfig.class)   // seguridad real (design D7 de auth-jwt-filter)
class AuthControllerTest {

    static final String URL = "/api/v1/auth/login";
    static final String TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.c2lnbmF0dXJl";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthService authService;

    ResultActions login(String json) throws Exception {
        return mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    @Test
    void validCredentialsAre200WithTheToken() throws Exception {
        when(authService.login(any())).thenReturn(new LoginResponse(TOKEN, "Bearer", 3600));

        MvcResult result = login("{\"email\":\"standard@shoplab.dev\",\"password\":\"Secret_123\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value(TOKEN))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andReturn();
        ContractAssertions.assertMatchesSchema(body(result), "login-response");
    }

    @Test
    void serviceReceivesTheNormalizedEmail() throws Exception {
        when(authService.login(any())).thenReturn(new LoginResponse(TOKEN, "Bearer", 3600));

        login("{\"email\":\"  Standard@ShopLab.dev \",\"password\":\"Secret_123\"}").andExpect(status().isOk());

        ArgumentCaptor<LoginRequest> captor = ArgumentCaptor.forClass(LoginRequest.class);
        verify(authService).login(captor.capture());
        assertThat(captor.getValue().email()).isEqualTo("standard@shoplab.dev");
    }

    @Test
    void emptyBodyReportsBothFieldsSorted() throws Exception {
        MvcResult result = login("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(2)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("must not be blank"))
                .andExpect(jsonPath("$.fieldErrors[1].field").value("password"))
                .andExpect(jsonPath("$.fieldErrors[1].message").value("must not be blank"))
                .andReturn();
        ContractAssertions.assertMatchesSchema(body(result), "error-response");
        verifyNoInteractions(authService);
    }

    @Test
    void blankPasswordIs400ForPasswordOnly() throws Exception {
        login("{\"email\":\"standard@shoplab.dev\",\"password\":\"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"));
        verifyNoInteractions(authService);
    }

    @Test
    void emailWithoutEmailFormatReachesTheService() throws Exception {
        // §7.2: no se valida el formato del email; el service responderá 401
        when(authService.login(any())).thenThrow(new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        login("{\"email\":\"not-an-email\",\"password\":\"Secret_123\"}")
                .andExpect(status().isUnauthorized());
        verify(authService).login(any());
    }

    @ParameterizedTest
    @EnumSource(value = ErrorCode.class, names = {"INVALID_CREDENTIALS", "USER_LOCKED"})
    void businessErrorUsesItsStatusAndMessage(ErrorCode errorCode) throws Exception {
        when(authService.login(any())).thenThrow(new BusinessException(errorCode));

        MvcResult result = login("{\"email\":\"standard@shoplab.dev\",\"password\":\"Secret_123\"}")
                .andExpect(status().is(errorCode.getStatus().value()))
                .andExpect(jsonPath("$.code").value(errorCode.name()))
                .andExpect(jsonPath("$.message").value(errorCode.getMessage()))
                .andExpect(jsonPath("$.fieldErrors", hasSize(0)))
                .andReturn();
        ContractAssertions.assertMatchesSchema(body(result), "error-response");
    }

    @Test
    void getIs405() throws Exception {
        MvcResult result = mockMvc.perform(get(URL))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andReturn();
        ContractAssertions.assertMatchesSchema(body(result), "error-response");
    }
}
