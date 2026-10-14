package com.shoplab.api.order;

import com.shoplab.api.common.exception.BusinessException;
import com.shoplab.api.common.exception.ErrorCode;
import com.shoplab.api.order.dto.CreateOrderRequest;
import com.shoplab.api.order.dto.OrderItemRequest;
import com.shoplab.api.order.dto.OrderItemResponse;
import com.shoplab.api.order.dto.OrderResponse;
import com.shoplab.api.order.dto.ShippingRequest;
import com.shoplab.api.product.Product;
import com.shoplab.api.product.ProductRepository;
import com.shoplab.api.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reglas de órdenes (§7.4 y §10) con los repositories mockeados, el mapper real y un reloj fijo (design D4).
 */
class OrderServiceImplTest {

    static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");
    static final ShippingRequest SHIPPING = new ShippingRequest("Calle 10 #20-30", "Bogotá", "110111", "CO");

    OrderRepository orderRepository;
    ProductRepository productRepository;
    UserRepository userRepository;
    OrderService orderService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        productRepository = mock(ProductRepository.class);
        userRepository = mock(UserRepository.class);
        orderService = new OrderServiceImpl(orderRepository, productRepository, userRepository, new OrderMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        when(userRepository.existsById(1L)).thenReturn(true);
        when(userRepository.existsById(3L)).thenReturn(true);
        givenProduct(1L, "SL-HEAD-001", "59.99");
        givenProduct(4L, "SL-MOUS-004", "25.00");
        when(productRepository.decreaseStock(any(), any(Integer.class))).thenReturn(1);
        // save devuelve la misma orden con el id que pondría la BD
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            ReflectionTestUtils.setField(order, "id", 3L);
            return order;
        });
    }

    void givenProduct(long id, String sku, String price) {
        Product product = new Product(sku, "Product " + id, "desc", new BigDecimal(price), 10, "key" + id, NOW);
        ReflectionTestUtils.setField(product, "id", id);
        when(productRepository.findById(id)).thenReturn(Optional.of(product));
    }

    static CreateOrderRequest request(OrderItemRequest... items) {
        return new CreateOrderRequest(List.of(items), PaymentMethod.CARD, SHIPPING);
    }

    static OrderItemRequest item(long productId, int quantity) {
        return new OrderItemRequest(productId, quantity);
    }

    @Test
    void createBuildsAConfirmedOrderWithCatalogPrices() {
        OrderResponse response = orderService.create(1L, request(item(1, 2), item(4, 1)));

        assertThat(response.id()).isEqualTo(3L);
        assertThat(response.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.CARD);
        assertThat(response.createdAt()).isEqualTo(NOW);
        assertThat(response.total()).isEqualTo(new BigDecimal("144.98"));
        assertThat(response.items())
                .extracting(OrderItemResponse::productId, OrderItemResponse::unitPrice, OrderItemResponse::subtotal)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1L, new BigDecimal("59.99"), new BigDecimal("119.98")),
                        org.assertj.core.groups.Tuple.tuple(4L, new BigDecimal("25.00"), new BigDecimal("25.00")));
        assertThat(response.shipping().city()).isEqualTo("Bogotá");
    }

    @Test
    void createDecreasesStockPerItemInRequestOrderAndSavesOnce() {
        orderService.create(1L, request(item(4, 1), item(1, 2)));

        var inOrder = inOrder(productRepository);
        inOrder.verify(productRepository).decreaseStock(4L, 1);
        inOrder.verify(productRepository).decreaseStock(1L, 2);

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, times(1)).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(1L);
        assertThat(saved.getValue().getItems()).extracting(i -> i.getProduct().getId()).containsExactly(4L, 1L);
        assertThat(saved.getValue().getShippingPostalCode()).isEqualTo("110111");
    }

    @Test
    void findAllByUserMapsTheRepositoryOrders() {
        when(orderRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(3L)).thenReturn(List.of(OrderMapperTest.sampleOrder()));

        assertThat(orderService.findAllByUser(3L)).extracting(OrderResponse::id).containsExactly(3L);
    }

    @Test
    void findByIdForUserReturnsTheOrder() {
        when(orderRepository.findByIdAndUserId(3L, 3L)).thenReturn(Optional.of(OrderMapperTest.sampleOrder()));

        assertThat(orderService.findByIdForUser(3L, 3L).id()).isEqualTo(3L);
    }

    void assertRejected(Runnable call, ErrorCode expected, String message) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .hasMessage(message)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(expected);
        verify(orderRepository, never()).save(any());
    }

    /** Reglas que van ANTES del loop de ítems: no se toca el stock. */
    @Nested
    class BeforeTheItems {

        @Test
        void emptyItemsIsEmptyOrder() {
            assertRejected(() -> orderService.create(1L, request()), ErrorCode.EMPTY_ORDER,
                    "Order must contain at least one item");
            verify(productRepository, never()).decreaseStock(anyLong(), anyInt());
        }

        @Test
        void repeatedProductReportsTheFirstRepeatedId() {
            assertRejected(() -> orderService.create(1L, request(item(1, 1), item(4, 1), item(1, 2))),
                    ErrorCode.DUPLICATE_ORDER_ITEM, "Product 1 appears more than once in the order");
            verify(productRepository, never()).decreaseStock(anyLong(), anyInt());
        }

        @Test
        void repeatedUnknownProductIsDuplicateBeforeNotFound() {
            assertRejected(() -> orderService.create(1L, request(item(99, 1), item(99, 1))),
                    ErrorCode.DUPLICATE_ORDER_ITEM, "Product 99 appears more than once in the order");
            verify(productRepository, never()).findById(anyLong());
        }

        @Test
        void deletedUserIsTokenInvalid() {
            when(userRepository.existsById(7L)).thenReturn(false);

            assertRejected(() -> orderService.create(7L, request(item(1, 1))), ErrorCode.AUTH_TOKEN_INVALID,
                    "Authentication token is invalid");
            verify(productRepository, never()).decreaseStock(anyLong(), anyInt());
        }

        @Test
        void emptyOrderIsCheckedBeforeTheUser() {
            when(userRepository.existsById(7L)).thenReturn(false);

            assertRejected(() -> orderService.create(7L, request()), ErrorCode.EMPTY_ORDER,
                    "Order must contain at least one item");
        }
    }

    /** Reglas por ítem, en el orden del request (§7.4: se reporta el primero que falla). */
    @Nested
    class PerItem {

        @Test
        void unknownProductIsProductNotFound() {
            when(productRepository.findById(99L)).thenReturn(Optional.empty());

            assertRejected(() -> orderService.create(1L, request(item(99, 1))), ErrorCode.PRODUCT_NOT_FOUND,
                    "Product 99 not found");
        }

        @Test
        void notEnoughStockIsInsufficientStock() {
            when(productRepository.decreaseStock(4L, 2)).thenReturn(0);

            assertRejected(() -> orderService.create(1L, request(item(1, 1), item(4, 2))), ErrorCode.INSUFFICIENT_STOCK,
                    "Insufficient stock for product 4");
        }

        @Test
        void firstItemWithoutStockWinsOverSecondUnknown() {
            givenProduct(3L, "SL-WATC-003", "149.00");
            when(productRepository.decreaseStock(3L, 1)).thenReturn(0);
            when(productRepository.findById(99L)).thenReturn(Optional.empty());

            assertRejected(() -> orderService.create(1L, request(item(3, 1), item(99, 1))), ErrorCode.INSUFFICIENT_STOCK,
                    "Insufficient stock for product 3");
        }

        @Test
        void firstItemUnknownWinsOverSecondWithoutStock() {
            givenProduct(3L, "SL-WATC-003", "149.00");
            when(productRepository.decreaseStock(3L, 1)).thenReturn(0);
            when(productRepository.findById(99L)).thenReturn(Optional.empty());

            assertRejected(() -> orderService.create(1L, request(item(99, 1), item(3, 1))), ErrorCode.PRODUCT_NOT_FOUND,
                    "Product 99 not found");
            verify(productRepository, never()).decreaseStock(anyLong(), anyInt());
        }
    }

    /** Consultas: el usuario del token debe existir ANTES de buscar órdenes (decisión del usuario, design D4). */
    @Nested
    class Queries {

        @Test
        void otherUsersOrderIsNotFound() {
            when(orderRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.empty());

            assertRejected(() -> orderService.findByIdForUser(1L, 1L), ErrorCode.ORDER_NOT_FOUND, "Order 1 not found");
        }

        @Test
        void deletedUserCannotListOrders() {
            when(userRepository.existsById(7L)).thenReturn(false);

            assertRejected(() -> orderService.findAllByUser(7L), ErrorCode.AUTH_TOKEN_INVALID,
                    "Authentication token is invalid");
            verifyNoInteractions(orderRepository);
        }

        @Test
        void deletedUserCannotReadAnOrder() {
            when(userRepository.existsById(7L)).thenReturn(false);

            assertRejected(() -> orderService.findByIdForUser(1L, 7L), ErrorCode.AUTH_TOKEN_INVALID,
                    "Authentication token is invalid");
            verifyNoInteractions(orderRepository);
        }
    }
}
