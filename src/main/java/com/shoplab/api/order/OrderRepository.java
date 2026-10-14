package com.shoplab.api.order;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // Órdenes + ítems + productos en UNA consulta: evita el problema N+1 (§11, regla 5).
    // Desempate por id: dos órdenes con el mismo createdAt salen siempre en el mismo orden (design D5)
    @EntityGraph(attributePaths = {"items", "items.product"})
    List<Order> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    // "No existe" y "es de otro usuario" se resuelven en la misma consulta → el mismo 404 (§7)
    @EntityGraph(attributePaths = {"items", "items.product"})
    Optional<Order> findByIdAndUserId(Long id, Long userId);
}
