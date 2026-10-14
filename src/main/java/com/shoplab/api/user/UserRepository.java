package com.shoplab.api.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // El email llega ya normalizado (minúsculas, sin espacios) desde el DTO
    boolean existsByEmail(String email);

    // Lo usa AuthServiceImpl para el login (concesión de §3.3: auth lee el repository de user)
    Optional<User> findByEmail(String email);
}
