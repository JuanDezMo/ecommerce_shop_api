package com.shoplab.api.order;

import com.shoplab.api.config.OpenApiConfig;
import com.shoplab.api.order.dto.CreateOrderRequest;
import com.shoplab.api.order.dto.OrderResponse;
import com.shoplab.api.security.AuthUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Órdenes del usuario autenticado. Todas las rutas son protegidas (§7.1).
 * Al service solo se le pasa el id del token, nunca el AuthUser (§3.2).
 */
@RestController
@RequestMapping("/api/v1/orders")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)   // candado en Swagger UI
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@AuthenticationPrincipal AuthUser user,
                                                @Valid @RequestBody CreateOrderRequest request) {
        OrderResponse created = orderService.create(user.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> findAll(@AuthenticationPrincipal AuthUser user) {
        return ResponseEntity.ok(orderService.findAllByUser(user.id()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> findById(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return ResponseEntity.ok(orderService.findByIdForUser(id, user.id()));
    }
}
