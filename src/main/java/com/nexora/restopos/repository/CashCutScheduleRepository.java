package com.nexora.restopos.repository;

import com.nexora.restopos.model.CashCutSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de {@link CashCutSchedule}, la configuración del job de cierre automático de cortes.
 *
 * <p>A diferencia del resto de las entidades del dominio, no es una tabla por tienda: existe una
 * única fila (id=1) que define la hora en que el job corre para <b>todas</b> las tiendas por igual.
 * No define consultas propias; el CRUD estándar de {@link JpaRepository} es suficiente ya que
 * solo se opera sobre ese único registro.
 */
public interface CashCutScheduleRepository extends JpaRepository<CashCutSchedule, Long> {
}
