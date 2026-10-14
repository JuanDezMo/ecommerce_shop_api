package com.shoplab.api.order;

import com.shoplab.api.order.dto.OrderItemResponse;
import com.shoplab.api.order.dto.OrderResponse;
import com.shoplab.api.order.dto.ShippingResponse;
import com.shoplab.api.product.Product;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Convierte Order → OrderResponse. Se llama dentro de la transacción del service (§11, regla 1),
 * porque lee los productos de los ítems (relación lazy). Nunca lee product.getStock() (§11, regla 6).
 */
@Component
public class OrderMapper {

    public OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(this::toItemResponse)
                .toList();
        ShippingResponse shipping = new ShippingResponse(order.getShippingAddress(), order.getShippingCity(),
                order.getShippingPostalCode(), order.getShippingCountry());
        return new OrderResponse(order.getId(), order.getStatus(), order.getPaymentMethod(), items,
                order.getTotal(), shipping, order.getCreatedAt());
    }

    private OrderItemResponse toItemResponse(OrderItem item) {
        Product product = item.getProduct();
        // unitPrice tiene 2 decimales, así que el subtotal también: 25.00 × 2 = 50.00
        BigDecimal subtotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        return new OrderItemResponse(product.getId(), product.getSku(), product.getName(), product.getImageKey(),
                item.getQuantity(), item.getUnitPrice(), subtotal);
    }
}
