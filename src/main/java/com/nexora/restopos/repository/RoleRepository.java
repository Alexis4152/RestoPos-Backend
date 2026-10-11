package com.nexora.restopos.repository;

import com.nexora.restopos.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio de {@link Role}.
 *
 * <p>A diferencia de la mayoría de las entidades del dominio, un {@link Role} no es global:
 * pertenece a una sola tienda (campo {@code tienda}) salvo los roles de plataforma
 * (por ejemplo SUPER_ADMIN) o los templates originales previos a la migración multi-tienda,
 * donde {@code tienda} es {@code null}. El borrado de un rol es siempre suave
 * (campo {@code isActive=false}), por lo que los listados usados en la UI filtran
 * explícitamente {@code isActive = true} para no mostrar roles eliminados.
 */
public interface RoleRepository extends JpaRepository<Role, Long> {
    // tienda-less (SUPER_ADMIN, o los templates originales antes de migrarlos)
    /** Busca un rol sin tienda (de plataforma o template) por nombre. */
    Optional<Role> findByName(String name);
    /** Busca el primer rol sin tienda con ese nombre, ordenado por id; usado cuando puede haber duplicados de un mismo template. */
    Optional<Role> findFirstByNameOrderById(String name);
    /** Lista todos los roles del sistema, ordenados alfabéticamente. */
    List<Role> findAllByOrderByNameAsc();
    /** Lista los roles sin tienda asignada (roles de plataforma o templates originales). */
    List<Role> findAllByTiendaIsNull();
    /** Busca el primer rol de plataforma (sin tienda) con ese nombre, ordenado por id. */
    Optional<Role> findFirstByNameAndTiendaIsNullOrderById(String name);
    /** Lista todos los roles de sistema (ej. SUPER_ADMIN, SUPERVISOR, ADMIN), de cualquier tienda. */
    List<Role> findAllByIsSystemTrue();

    /** Busca el rol de una tienda por nombre. */
    Optional<Role> findByNameAndTiendaId(String name, Long tiendaId);
    /** Indica si ya existe un rol con ese nombre dentro de la tienda dada (unicidad por tienda+nombre). */
    boolean existsByNameAndTiendaId(String name, Long tiendaId);

    // listados para la pantalla de Roles y el selector de rol al dar de alta un usuario —
    // no deben mostrar roles eliminados (borrado suave, ver isActive).
    /** Lista todos los roles activos (no eliminados) del sistema, ordenados alfabéticamente. */
    List<Role> findAllByIsActiveTrueOrderByNameAsc();
    /** Lista los roles activos (no eliminados) de una tienda, ordenados alfabéticamente. */
    List<Role> findAllByTiendaIdAndIsActiveTrueOrderByNameAsc(Long tiendaId);
}
