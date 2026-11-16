package com.nexora.restopos.repository;

import com.nexora.restopos.model.MenuDia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface MenuDiaRepository extends JpaRepository<MenuDia, Long> {

    List<MenuDia> findByTiendaIdOrderByNombreAsc(Long tiendaId);

    Optional<MenuDia> findFirstByTiendaIdAndHoyTrue(Long tiendaId);

    boolean existsByTiendaIdAndNombreIgnoreCase(Long tiendaId, String nombre);

    boolean existsByTiendaIdAndNombreIgnoreCaseAndIdNot(Long tiendaId, String nombre, Long id);

    /** Ids de los platillos del menú de hoy de la tienda (vacío también si no hay menú de hoy). */
    @Query("SELECT p.id FROM MenuDia m JOIN m.productos p WHERE m.tienda.id = :tiendaId AND m.hoy = true")
    Set<Long> findProductoIdsDelMenuHoy(@Param("tiendaId") Long tiendaId);

    @Query("SELECT COUNT(m) > 0 FROM MenuDia m WHERE m.tienda.id = :tiendaId AND m.hoy = true")
    boolean existsMenuHoy(@Param("tiendaId") Long tiendaId);

    /** Quita la marca de "hoy" a los menús de la tienda, salvo {@code exceptoId} (null = a todos). */
    @Modifying
    @Query("UPDATE MenuDia m SET m.hoy = false WHERE m.tienda.id = :tiendaId AND m.hoy = true " +
           "AND (:exceptoId IS NULL OR m.id <> :exceptoId)")
    int quitarHoy(@Param("tiendaId") Long tiendaId, @Param("exceptoId") Long exceptoId);
}
