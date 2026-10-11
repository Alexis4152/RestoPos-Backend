package com.nexora.restopos.repository;

import com.nexora.restopos.model.Comensal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComensalRepository extends JpaRepository<Comensal, Long> {
    List<Comensal> findByOrdenIdOrderByNumeroAsc(Long ordenId);
    int countByOrdenId(Long ordenId);
}
