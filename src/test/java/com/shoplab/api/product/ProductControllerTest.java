package com.shoplab.api.product;

import com.shoplab.api.common.exception.BusinessException;
import com.shoplab.api.common.exception.ErrorCode;
import com.shoplab.api.common.exception.GlobalExceptionHandler;
import com.shoplab.api.product.dto.ProductResponse;
import com.shoplab.api.security.SecurityConfig;
import com.shoplab.api.support.ContractAssertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Capa web del catálogo con el service mockeado (design D5).
 */
@WebMvcTest(ProductController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ProductControllerTest {

    static final ProductResponse HEADPHONES = new ProductResponse(1L, "SL-HEAD-001", "Wireless Headphones",
            "Over-ear bluetooth headphones", new BigDecimal("59.99"), 10, "headphones");
    static final ProductResponse MOUSE = new ProductResponse(4L, "SL-MOUS-004", "Gaming Mouse",
            "Ergonomic gaming mouse with 6 buttons", new BigDecimal("25.00"), 20, "mouse");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ProductService productService;

    static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    @Test
    void listReturnsProductsInServiceOrder() throws Exception {
        when(productService.findAll()).thenReturn(List.of(HEADPHONES, MOUSE));

        MvcResult result = mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[1].id").value(4))
                .andReturn();
        ContractAssertions.assertMatchesSchema(body(result), "product-list");
    }

    @Test
    void detailReturnsTheSevenFields() throws Exception {
        when(productService.findById(1L)).thenReturn(HEADPHONES);

        MvcResult result = mockMvc.perform(get("/api/v1/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.sku").value("SL-HEAD-001"))
                .andExpect(jsonPath("$.name").value("Wireless Headphones"))
                .andExpect(jsonPath("$.description").value("Over-ear bluetooth headphones"))
                .andExpect(jsonPath("$.price").value(59.99))
                .andExpect(jsonPath("$.stock").value(10))
                .andExpect(jsonPath("$.imageKey").value("headphones"))
                .andReturn();
        ContractAssertions.assertMatchesSchema(body(result), "product-response");
    }

    @Test
    void priceIsANumberWithTwoDecimals() throws Exception {
        when(productService.findById(4L)).thenReturn(MOUSE);

        MvcResult result = mockMvc.perform(get("/api/v1/products/4"))
                .andExpect(status().isOk())
                .andReturn();

        // design D4: 25.00 como número, ni 25 ni "25.00"
        assertThat(body(result)).contains("\"price\":25.00");
    }

    @Nested
    class Errors {

        @Test
        void unknownProductIs404WithTheId() throws Exception {
            when(productService.findById(99L)).thenThrow(new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, 99L));

            MvcResult result = mockMvc.perform(get("/api/v1/products/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("Product 99 not found"))
                    .andExpect(jsonPath("$.path").value("/api/v1/products/99"))
                    .andExpect(jsonPath("$.fieldErrors", hasSize(0)))
                    .andReturn();
            ContractAssertions.assertMatchesSchema(body(result), "error-response");
        }

        @ParameterizedTest
        @ValueSource(strings = {"abc", "1.5"})
        void nonNumericIdIs400WithoutCallingTheService(String id) throws Exception {
            MvcResult result = mockMvc.perform(get("/api/v1/products/" + id))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message").value("Request has invalid fields"))
                    .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("id"))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value("must be an integer"))
                    .andReturn();
            ContractAssertions.assertMatchesSchema(body(result), "error-response");
            verify(productService, never()).findById(any());
        }

        @Test
        void deleteIs405() throws Exception {
            MvcResult result = mockMvc.perform(delete("/api/v1/products/1"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                    .andReturn();
            ContractAssertions.assertMatchesSchema(body(result), "error-response");
        }

        @Test
        void postIs405() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/v1/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"sku\":\"NEW-001\"}"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                    .andReturn();
            ContractAssertions.assertMatchesSchema(body(result), "error-response");
        }
    }
}
