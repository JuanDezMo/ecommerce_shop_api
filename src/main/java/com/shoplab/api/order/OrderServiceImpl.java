package com.shoplab.api.order;

import com.shoplab.api.common.exception.BusinessException;
import com.shoplab.api.common.exception.ErrorCode;
import com.shoplab.api.order.dto.CreateOrderRequest;
import com.shoplab.api.order.dto.OrderItemRequest;
import com.shoplab.api.order.dto.OrderResponse;
import com.shoplab.api.product.Product;
import com.shoplab.api.product.ProductRepository;
import com.shoplab.api.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Usa ProductRepository y UserRepository directamente (concesión de §3.3, solo lectura + decreaseStock).
 * @Transactional a nivel de clase: si un ítem falla, el rollback deshace los descuentos de stock anteriores (§10).
 */
@Service
@Transactional
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderMapper orderMapper;
    private final Clock clock;

    public OrderServiceImpl(OrderRepository orderRepository, ProductRepository productRepository,
                            UserRepository userRepository, OrderMapper orderMapper, Clock clock) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.orderMapper = orderMapper;
        this.clock = clock;
    }

    @Override
    public OrderResponse create(Long userId, CreateOrderRequest request) {
        // Reglas en el orden de §7.4: orden vacía, producto repetido, usuario existente, y luego cada ítem
        if (request.items().isEmpty()) {
            throw new BusinessException(ErrorCode.EMPTY_ORDER);
        }
        Long duplicatedProductId = firstDuplicatedProductId(request.items());
        if (duplicatedProductId != null) {
            throw new BusinessException(ErrorCode.DUPLICATE_ORDER_ITEM, duplicatedProductId);
        }
        requireExistingUser(userId);

        Order order = new Order();
        order.setUserId(userId);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setPaymentMethod(request.paymentMethod());
        order.setShippingAddress(request.shipping().address());
        order.setShippingCity(request.shipping().city());
        order.setShippingPostalCode(request.shipping().postalCode());
        order.setShippingCountry(request.shipping().country());
        // Truncado a microsegundos como lo guarda PostgreSQL: el POST y un GET posterior devuelven lo mismo
        order.setCreatedAt(Instant.now(clock).truncatedTo(ChronoUnit.MICROS));

        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemRequest item : request.items()) {   // en el orden del request
            Product product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, item.productId()));
            // Atómico: 0 filas = no alcanza. La excepción hace rollback de los descuentos anteriores (§10)
            if (productRepository.decreaseStock(product.getId(), item.quantity()) == 0) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK, product.getId());
            }
            // El precio es el del catálogo en este momento, nunca el que mande la app
            BigDecimal unitPrice = product.getPrice();
            order.addItem(new OrderItem(product, item.quantity(), unitPrice));
            total = total.add(unitPrice.multiply(BigDecimal.valueOf(item.quantity())));
        }
        order.setTotal(total.setScale(2, RoundingMode.HALF_UP));   // §11, regla 8

        Order saved = orderRepository.save(order);
        return orderMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> findAllByUser(Long userId) {
        requireExistingUser(userId);
        return orderRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                .map(orderMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse findByIdForUser(Long orderId, Long userId) {
        requireExistingUser(userId);
        // "No existe" y "es de otro usuario" dan el mismo 404: no se revela que la orden existe (§7)
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, orderId));
        return orderMapper.toResponse(order);
    }

    /**
     * Un token válido puede ser de un usuario que ya no existe (por ejemplo, tras /test/reset).
     * Al crear evita un 500 por la clave foránea; en las consultas evita responder 200 o 404 a un usuario
     * inexistente (design D4, decisión tomada en la revisión del change).
     */
    private void requireExistingUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
    }

    /** El primer productId que aparece por segunda vez, según el orden del request; null si no hay repetidos. */
    private static Long firstDuplicatedProductId(List<OrderItemRequest> items) {
        Set<Long> seen = new HashSet<>();
        for (OrderItemRequest item : items) {
            if (!seen.add(item.productId())) {
                return item.productId();
            }
        }
        return null;
    }
}
