package com.shoplab.api.common.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessExceptionTest {

    @Test
    void formatsTheMessageWithTheResourceId() {
        BusinessException ex = new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, 99L);

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
        assertThat(ex.getMessage()).isEqualTo("Product 99 not found");
    }

    @Test
    void usesTheCatalogMessageWhenThereAreNoArguments() {
        BusinessException ex = new BusinessException(ErrorCode.USER_LOCKED);

        assertThat(ex.getMessage()).isEqualTo("User is locked");
    }
}
