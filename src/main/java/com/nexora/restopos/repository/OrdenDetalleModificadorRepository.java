package com.nexora.restopos.repository;

import com.nexora.restopos.model.OrdenDetalleModificador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/** Repositorio de {@link OrdenDetalleModificador} — filas creadas una sola vez al agregar el producto. */
public interface OrdenDetalleModificadorRepository extends JpaRepository<OrdenDetalleModificador, Long> {

    /**
     * Cuántas piezas de un producto se pidieron con cada opción desde {@code desde}, sin contar
     * lo cancelado: filas {@code [grupoNombre, nombre, piezas]}. Va por nombre porque la línea
     * guarda una copia del texto (no la opción), así sobrevive a que el admin la edite. Lo usa
     * ProductService#sugerencias (modo rápido: opción preseleccionada).
     */
    @Query("SELECT m.grupoNombre, m.nombre, SUM(d.cantidad) FROM OrdenDetalleModificador m JOIN m.ordenDetalle d " +
           "WHERE d.producto.id = :productoId AND d.estado <> com.nexora.restopos.model.OrdenDetalleEstado.CANCELADO " +
           "AND d.createdAt >= :desde GROUP BY m.grupoNombre, m.nombre")
    List<Object[]> contarOpciones(@Param("productoId") Long productoId, @Param("desde") LocalDateTime desde);
}
