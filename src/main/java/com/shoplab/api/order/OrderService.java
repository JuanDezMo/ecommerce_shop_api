package com.shoplab.api.order;

import com.shoplab.api.order.dto.CreateOrderRequest;
import com.shoplab.api.order.dto.OrderResponse;

import java.util.List;

/**
 * Órdenes del usuario autenticado. Recibe el id del token (nunca AuthUser, §3.2).
 * Devuelve DTOs o lanza BusinessException; nunca Optional ni null.
 */
public interface OrderService {

    /** Crea la orden y descuenta el stock. Reglas en el orden de §7.4. */
    OrderResponse create(Long userId, CreateOrderRequest request);

    /** Órdenes del usuario, de la más reciente a la más antigua. */
    List<OrderResponse> findAllByUser(Long userId);

    /** Una orden del usuario; ORDER_NOT_FOUND si no existe o es de otro usuario. */
    OrderResponse findByIdForUser(Long orderId, Long userId);
}
