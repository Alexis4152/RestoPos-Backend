package com.nexora.restopos.repository;

import com.nexora.restopos.model.MovimientoCaja;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovimientoCajaRepository extends JpaRepository<MovimientoCaja, Long> {
    List<MovimientoCaja> findByCashCutIdOrderByCreatedAtAsc(Long cashCutId);
}
