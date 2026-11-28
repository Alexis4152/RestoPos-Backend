package com.nexora.restopos.repository;

import com.nexora.restopos.model.Role;
import com.nexora.restopos.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio de {@link User}.
 *
 * <p>El borrado de un usuario es siempre suave (campo {@code isActive=false}); ver también
 * los campos de auditoría {@code createdBy}/{@code updatedBy}/{@code deletedBy} de la entidad,
 * cargados en {@code FetchType.LAZY} a propósito para no encadenar el join
 * {@code User → createdBy(User) → createdBy(User) → ...} sin límite, que provocaba que Postgres
 * tronara por límite de profundidad de stack.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /** El supervisor de una tienda: la relación vive en Tienda, él no tiene tienda propia. */
    @Query("SELECT t.supervisor FROM Tienda t WHERE t.id = :tiendaId")
    java.util.Optional<User> findSupervisorOfTienda(@Param("tiendaId") Long tiendaId);
    /** Busca un usuario por su correo electrónico (usado en el login). */
    Optional<User> findByEmail(String email);
    /** Indica si ya existe un usuario registrado con ese correo. */
    boolean existsByEmail(String email);
    /** Lista todos los usuarios, ordenados alfabéticamente por nombre. */
    List<User> findAllByOrderByNameAsc();
    /** Cuenta cuántos usuarios tienen asignado el rol dado (usado para validar antes de eliminar/desactivar un rol). */
    long countByRole(Role role);

    // from/to siempre vienen con un valor real (nunca null) — ver UserService.findAll,
    // porque Postgres no logra inferir el tipo de un parámetro timestamp nulo.
    /**
     * Búsqueda de usuarios con filtros combinables (usada en la pantalla de administración de usuarios).
     *
     * <p>{@code tiendaId} nulo indica SUPER_ADMIN viendo todas las tiendas (no se filtra por
     * tienda); cualquier otro valor restringe el resultado a esa tienda. {@code name}/{@code email}
     * hacen coincidencia parcial insensible a mayúsculas (nulo = sin filtrar); {@code roleId} e
     * {@code isActive} son opcionales (nulo = cualquier valor). {@code from}/{@code to} siempre
     * llegan con un valor real desde el service (nunca {@code null}), por la limitación de Postgres
     * para inferir el tipo de un parámetro timestamp nulo (ver comentario arriba).
     *
     * <p>{@code extraUserId} suma UN usuario puntual al resultado aunque no pertenezca a
     * {@code tiendaId} — usado por {@code UserService#findAll} para que, viendo la lista de
     * una tienda, SUPER_ADMIN vea también al Supervisor asignado a esa tienda (que por ser
     * rol de plataforma no tiene {@code tienda_id} propio, así que de otra forma nunca
     * calzaría con el filtro de arriba). {@code null} no suma a nadie.</p>
     */
    @Query("SELECT u FROM User u WHERE " +
           "(:tiendaId IS NULL OR u.tienda.id = :tiendaId OR u.id = :extraUserId) " +
           "AND u.createdAt BETWEEN :from AND :to " +
           "AND (:name IS NULL OR LOWER(u.name) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%'))) " +
           "AND (:email IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:email AS string), '%'))) " +
           "AND (:roleId IS NULL OR u.role.id = :roleId) " +
           "AND (:isActive IS NULL OR u.isActive = :isActive) " +
           "ORDER BY u.name ASC")
    List<User> search(@Param("tiendaId") Long tiendaId,
                       @Param("from") LocalDateTime from,
                       @Param("to") LocalDateTime to,
                       @Param("name") String name,
                       @Param("email") String email,
                       @Param("roleId") Long roleId,
                       @Param("isActive") Boolean isActive,
                       @Param("extraUserId") Long extraUserId);

    /** Lo mismo que {@link #search}, por páginas (pantalla de Usuarios), ordenado por nombre. */
    @Query(value = "SELECT u FROM User u WHERE " +
           "(:tiendaId IS NULL OR u.tienda.id = :tiendaId OR u.id = :extraUserId) " +
           "AND u.createdAt BETWEEN :from AND :to " +
           "AND (:name IS NULL OR LOWER(u.name) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%'))) " +
           "AND (:email IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:email AS string), '%'))) " +
           "AND (:roleId IS NULL OR u.role.id = :roleId) " +
           "AND (:isActive IS NULL OR u.isActive = :isActive) "+
           "ORDER BY u.name ASC, u.id ASC",
           countQuery = "SELECT COUNT(u) FROM User u WHERE " +
           "(:tiendaId IS NULL OR u.tienda.id = :tiendaId OR u.id = :extraUserId) " +
           "AND u.createdAt BETWEEN :from AND :to " +
           "AND (:name IS NULL OR LOWER(u.name) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%'))) " +
           "AND (:email IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:email AS string), '%'))) " +
           "AND (:roleId IS NULL OR u.role.id = :roleId) " +
           "AND (:isActive IS NULL OR u.isActive = :isActive) ")
    org.springframework.data.domain.Page<User> searchPage(@Param("tiendaId") Long tiendaId,
                       @Param("from") LocalDateTime from,
                       @Param("to") LocalDateTime to,
                       @Param("name") String name,
                       @Param("email") String email,
                       @Param("roleId") Long roleId,
                       @Param("isActive") Boolean isActive,
                       @Param("extraUserId") Long extraUserId,
                       org.springframework.data.domain.Pageable pageable);

    // para avisarle por correo al/los admin(es) de una tienda cuando se autocierra un corte
    /** Lista los usuarios activos con rol ADMIN de una tienda; usado para notificarles por correo cuando el job autocierra un corte. */
    @Query("SELECT u FROM User u WHERE u.tienda.id = :tiendaId AND u.role.name = 'ADMIN' AND u.isActive = true")
    List<User> findAdminsByTiendaId(@Param("tiendaId") Long tiendaId);

    // personal activo de una tienda — para los selects de "¿quién puso/retiró el dinero?" en cajas
    List<User> findByTiendaIdAndIsActiveTrueOrderByNameAsc(Long tiendaId);

    /** Meseros activos de una tienda (para asignarles una orden que abrió alguien más). */
    @Query("SELECT u FROM User u WHERE u.tienda.id = :tiendaId AND u.isActive = true AND u.role.name = 'MESERO' ORDER BY u.name ASC")
    List<User> findMeserosActivos(@Param("tiendaId") Long tiendaId);
}
