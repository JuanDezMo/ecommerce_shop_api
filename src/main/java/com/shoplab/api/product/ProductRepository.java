package com.shoplab.api.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // El nombre documenta la regla de §7.3: el catálogo siempre sale ordenado por id
    List<Product> findAllByOrderByIdAsc();
}
