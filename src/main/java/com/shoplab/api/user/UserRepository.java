package com.shoplab.api.user;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    // El email llega ya normalizado (minúsculas, sin espacios) desde el DTO
    boolean existsByEmail(String email);
}
