package com.nexora.restopos.repository;

import com.nexora.restopos.model.CashRegister;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CashRegisterRepository extends JpaRepository<CashRegister, Long> {

    List<CashRegister> findByTiendaIdOrderByIdAsc(Long tiendaId);

    boolean existsByTiendaId(Long tiendaId);

    /**
     * Igual que {@code findById}, pero con bloqueo pesimista de escritura: lo usan abrir,
     * cerrar y los movimientos con la caja cerrada, para que dos operaciones simultáneas
     * sobre la misma caja (dos cajeros abriéndola a la vez, un retiro mientras alguien la
     * abre) no partan del mismo estado leído.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM CashRegister r WHERE r.id = :id")
    Optional<CashRegister> findByIdForUpdate(@Param("id") Long id);
}
