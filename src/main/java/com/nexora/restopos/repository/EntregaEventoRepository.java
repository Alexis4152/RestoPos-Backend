package com.nexora.restopos.repository;

import com.nexora.restopos.model.EntregaEvento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EntregaEventoRepository extends JpaRepository<EntregaEvento, Long> {

    List<EntregaEvento> findByOrdenIdOrderByCreatedAtAscIdAsc(Long ordenId);
}
