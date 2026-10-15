package com.nexora.restopos.repository;

import com.nexora.restopos.model.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    Optional<Cliente> findByTiendaIdAndEmailIgnoreCase(Long tiendaId, String email);

    boolean existsByTiendaIdAndEmailIgnoreCase(Long tiendaId, String email);

    java.util.List<Cliente> findAllByTiendaIdAndIsActiveTrue(Long tiendaId);
}
