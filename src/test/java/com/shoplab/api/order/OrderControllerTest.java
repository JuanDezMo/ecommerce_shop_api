package com.shoplab.api.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shoplab.api.common.exception.BusinessException;
import com.shoplab.api.common.exception.ErrorCode;
import com.shoplab.api.order.dto.CreateOrderRequest;
import com.shoplab.api.support.ContractAssertions;
import com.shoplab.api.support.TestTokens;
import com.shoplab.api.support.WebMvcSecurityConfig;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Capa web de órdenes con el service mockeado y la seguridad real (tokens de TestTokens).
 */
@WebMvcTest(OrderController.class)
@Import(WebMvcSecurityConfig.class)
class OrderControllerTest {

    static final String URL = "/api/v1/orders";

    static final String VALID_BODY = """
            {
              "items": [ { "productId": 1, "quantity": 2 }, { "productId": 4, "quantity": 1 } ],
              "paymentMethod": "CARD",
              "shipping": { "address": "Calle 10 #20-30", "city": "Bogotá", "postalCode": "110111", "country": "CO" }
            }
            """;

    /** Token del usuario 1 (standard). */
    static final String TOKEN = TestTokens.bearer(TestTokens.valid(1L, "standard@shoplab.dev"));

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    OrderService orderService;

    ResultActions postOrder(String json) throws Exception {
        return mockMvc.perform(post(URL).header("Authorization", TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    @Test
    void validOrderIs201AndTheServiceGetsTheUserIdFromTheToken() throws Exception {
        when(orderService.create(eq(1L), any())).thenReturn(new OrderMapper().toResponse(OrderMapperTest.sampleOrder()));

        MvcResult result = postOrder(VALID_BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.total").value(144.98))
                .andReturn();

        ContractAssertions.assertMatchesSchema(body(result), "order-response");
        ArgumentCaptor<CreateOrderRequest> captor = ArgumentCaptor.forClass(CreateOrderRequest.class);
        verify(orderService).create(eq(1L), captor.capture());
        assertThat(captor.getValue().items()).hasSize(2);
        assertThat(captor.getValue().shipping().postalCode()).isEqualTo("110111");
    }

    @Test
    void listIs200WithTheUserOrders() throws Exception {
        when(orderService.findAllByUser(1L)).thenReturn(List.of(new OrderMapper().toResponse(OrderMapperTest.sampleOrder())));

        MvcResult result = mockMvc.perform(get(URL).header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andReturn();
        ContractAssertions.assertMatchesSchema(body(result), "order-list");
    }

    @Test
    void detailIs200() throws Exception {
        when(orderService.findByIdForUser(3L, 1L)).thenReturn(new OrderMapper().toResponse(OrderMapperTest.sampleOrder()));

        MvcResult result = mockMvc.perform(get(URL + "/3").header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andReturn();
        ContractAssertions.assertMatchesSchema(body(result), "order-response");
    }

    static final ObjectMapper MAPPER = new ObjectMapper();

    /** VALID_BODY con un cambio en una ruta JSON simple ("items", "items[0].quantity", "shipping.city"…). */
    static String validBodyWith(String path, String rawJsonValue) throws Exception {
        ObjectNode root = (ObjectNode) MAPPER.readTree(VALID_BODY);
        JsonNode value = MAPPER.readTree(rawJsonValue);
        if (path.equals("items[0].quantity")) {
            ((ObjectNode) root.get("items").get(0)).set("quantity", value);
        } else if (path.startsWith("shipping.")) {
            ((ObjectNode) root.get("shipping")).set(path.substring("shipping.".length()), value);
        } else {
            root.set(path, value);
        }
        return MAPPER.writeValueAsString(root);
    }

    @Nested
    class Validation {

        static Stream<Arguments> invalidBodies() {
            return Stream.of(
                    Arguments.of("items[0].quantity", "0", "must be between 1 and 99"),
                    Arguments.of("items[0].quantity", "100", "must be between 1 and 99"),
                    Arguments.of("items", "[null]", "must not be null"),
                    Arguments.of("items", "null", "must not be null"),
                    Arguments.of("paymentMethod", "null", "must not be null"),
                    Arguments.of("shipping", "null", "must not be null"),
                    Arguments.of("shipping.address", "\"Av 1\"", "size must be between 5 and 120"),
                    Arguments.of("shipping.city", "\"B\"", "size must be between 2 and 60"),
                    Arguments.of("shipping.postalCode", "\"1\"", "must be 3 to 10 letters, digits, spaces or hyphens"),
                    Arguments.of("shipping.country", "\"BR\"", "must be one of: US, CO, MX, ES, AR, CL, PE"));
        }

        @ParameterizedTest(name = "{0} = {1} → {2}")
        @MethodSource("invalidBodies")
        void invalidFieldIs400AndTheServiceIsNotCalled(String path, String rawJsonValue, String message) throws Exception {
            // "items": [null] reporta el elemento: items[0]
            String expectedField = rawJsonValue.equals("[null]") ? "items[0]" : path;

            MvcResult result = postOrder(validBodyWith(path, rawJsonValue))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value(expectedField))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value(message))
                    .andReturn();
            ContractAssertions.assertMatchesSchema(body(result), "error-response");
            verifyNoInteractions(orderService);
        }

        @Test
        void invalidShippingReportsEveryField() throws Exception {
            String json = validBodyWith("shipping", "{\"address\":\"Calle 10 #20-30\",\"city\":\"B\",\"postalCode\":\"1\",\"country\":\"CO\"}");

            postOrder(json)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors", hasSize(2)))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("shipping.city"))
                    .andExpect(jsonPath("$.fieldErrors[1].field").value("shipping.postalCode"));
            verifyNoInteractions(orderService);
        }

        static Stream<Arguments> wrongTypes() {
            return Stream.of(
                    Arguments.of("paymentMethod", "\"CRYPTO\"", "paymentMethod", "must be one of: CARD, CASH, TRANSFER"),
                    Arguments.of("items[0].quantity", "\"2\"", "items[0].quantity", "must be an integer"),
                    Arguments.of("items[0].quantity", "1.5", "items[0].quantity", "must be an integer"));
        }

        @ParameterizedTest(name = "{0} = {1} → {3}")
        @MethodSource("wrongTypes")
        void wrongTypeIs400WithOneFieldError(String path, String rawJsonValue, String field, String message) throws Exception {
            postOrder(validBodyWith(path, rawJsonValue))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value(field))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value(message));
            verifyNoInteractions(orderService);
        }

        @Test
        void validationRunsBeforeEmptyOrder() throws Exception {
            // items vacío pero sin shipping: gana la validación (§7.1, etapa 5 antes que la 6)
            postOrder("{\"items\":[],\"paymentMethod\":\"CARD\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("shipping"))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value("must not be null"));
            verifyNoInteractions(orderService);
        }

        @Test
        void emptyItemsPassValidationAndReachTheService() throws Exception {
            when(orderService.create(eq(1L), any())).thenThrow(new BusinessException(ErrorCode.EMPTY_ORDER));

            postOrder(validBodyWith("items", "[]"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("EMPTY_ORDER"))
                    .andExpect(jsonPath("$.fieldErrors", hasSize(0)));
        }
    }

    @Nested
    class BusinessErrors {

        static Stream<Arguments> serviceErrors() {
            return Stream.of(
                    Arguments.of(new BusinessException(ErrorCode.EMPTY_ORDER), 400, "Order must contain at least one item"),
                    Arguments.of(new BusinessException(ErrorCode.DUPLICATE_ORDER_ITEM, 1L), 400, "Product 1 appears more than once in the order"),
                    Arguments.of(new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, 99L), 404, "Product 99 not found"),
                    Arguments.of(new BusinessException(ErrorCode.INSUFFICIENT_STOCK, 3L), 409, "Insufficient stock for product 3"),
                    Arguments.of(new BusinessException(ErrorCode.AUTH_TOKEN_INVALID), 401, "Authentication token is invalid"));
        }

        @ParameterizedTest(name = "{2}")
        @MethodSource("serviceErrors")
        void createErrorsUseTheirStatusAndMessage(BusinessException error, int status, String message) throws Exception {
            when(orderService.create(eq(1L), any())).thenThrow(error);

            MvcResult result = postOrder(VALID_BODY)
                    .andExpect(status().is(status))
                    .andExpect(jsonPath("$.code").value(error.getErrorCode().name()))
                    .andExpect(jsonPath("$.message").value(message))
                    .andExpect(jsonPath("$.fieldErrors", hasSize(0)))
                    .andReturn();
            ContractAssertions.assertMatchesSchema(body(result), "error-response");
        }

        @Test
        void otherUsersOrderIs404() throws Exception {
            when(orderService.findByIdForUser(1L, 1L)).thenThrow(new BusinessException(ErrorCode.ORDER_NOT_FOUND, 1L));

            MvcResult result = mockMvc.perform(get(URL + "/1").header("Authorization", TOKEN))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("Order 1 not found"))
                    .andReturn();
            ContractAssertions.assertMatchesSchema(body(result), "error-response");
        }

        @Test
        void deletedUserListingOrdersIs401() throws Exception {
            when(orderService.findAllByUser(1L)).thenThrow(new BusinessException(ErrorCode.AUTH_TOKEN_INVALID));

            mockMvc.perform(get(URL).header("Authorization", TOKEN))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
        }
    }

    @Nested
    class Routes {

        @Test
        void withoutTokenIs401AndTheServiceIsNotCalled() throws Exception {
            mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_TOKEN_MISSING"));
            mockMvc.perform(get(URL))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_TOKEN_MISSING"));
            verifyNoInteractions(orderService);
        }

        @Test
        void putWithTokenIs405() throws Exception {
            mockMvc.perform(put(URL).header("Authorization", TOKEN).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        }

        @Test
        void nonNumericIdIs400() throws Exception {
            mockMvc.perform(get(URL + "/abc").header("Authorization", TOKEN))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("id"))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value("must be an integer"));
            verifyNoInteractions(orderService);
        }
    }
}
