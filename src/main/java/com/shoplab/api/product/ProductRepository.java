package com.shoplab.api.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // El nombre documenta la regla de §7.3: el catálogo siempre sale ordenado por id
    List<Product> findAllByOrderByIdAsc();

    /**
     * Descuenta stock solo si alcanza, en UNA sentencia: así dos compras simultáneas de la última unidad
     * no pueden pasar las dos (§10). Devuelve las filas cambiadas: 1 = descontado, 0 = no alcanza o no existe.
     * Después de llamarlo, no leas product.getStock() ni hagas save(product): la entidad en memoria
     * tiene el stock viejo (§11, regla 6). Lo usa OrderServiceImpl dentro de su transacción.
     */
    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock - :quantity WHERE p.id = :id AND p.stock >= :quantity")
    int decreaseStock(@Param("id") Long id, @Param("quantity") int quantity);
}
