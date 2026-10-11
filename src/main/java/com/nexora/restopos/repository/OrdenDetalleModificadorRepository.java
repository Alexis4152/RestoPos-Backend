package com.nexora.restopos.repository;

import com.nexora.restopos.model.OrdenDetalleModificador;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio de {@link OrdenDetalleModificador} — filas creadas una sola vez al agregar el producto. */
public interface OrdenDetalleModificadorRepository extends JpaRepository<OrdenDetalleModificador, Long> {
}
