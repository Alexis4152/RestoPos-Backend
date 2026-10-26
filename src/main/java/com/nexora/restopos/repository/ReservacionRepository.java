package com.nexora.restopos.repository;

import com.nexora.restopos.model.Reservacion;
import com.nexora.restopos.model.ReservacionEstado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservacionRepository extends JpaRepository<Reservacion, Long> {

    Optional<Reservacion> findByToken(String token);

    /** Reservaciones de una tienda en un rango (pantalla "Reservaciones" del personal), por hora. */
    @Query("SELECT r FROM Reservacion r WHERE (:tiendaId IS NULL OR r.tienda.id = :tiendaId) " +
           "AND r.fechaHora >= :desde AND r.fechaHora < :hasta ORDER BY r.fechaHora ASC, r.id ASC")
    List<Reservacion> buscar(@Param("tiendaId") Long tiendaId, @Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    /** Pendientes de confirmar (de cualquier fecha futura o de hoy), más próximas primero. */
    @Query("SELECT r FROM Reservacion r WHERE (:tiendaId IS NULL OR r.tienda.id = :tiendaId) " +
           "AND r.estado = com.nexora.restopos.model.ReservacionEstado.PENDIENTE AND r.fechaHora >= :desde " +
           "ORDER BY r.fechaHora ASC, r.id ASC")
    List<Reservacion> pendientes(@Param("tiendaId") Long tiendaId, @Param("desde") LocalDateTime desde);

    /** Reservaciones del cliente registrado, las más recientes primero. */
    List<Reservacion> findTop30ByClienteIdOrderByFechaHoraDesc(Long clienteId);

    /** Activas a futuro con ese teléfono en la tienda (tope anti-spam de reservaciones por persona). */
    @Query("SELECT COUNT(r) FROM Reservacion r WHERE r.tienda.id = :tiendaId AND r.telefono = :telefono " +
           "AND r.estado IN :estados AND r.fechaHora >= :desde")
    long contarActivasPorTelefono(@Param("tiendaId") Long tiendaId, @Param("telefono") String telefono,
                                  @Param("estados") Collection<ReservacionEstado> estados, @Param("desde") LocalDateTime desde);

    /** Por confirmar o confirmadas en esa mesa dentro de la ventana (no dar la misma mesa dos veces a la misma hora). */
    @Query("SELECT r FROM Reservacion r WHERE r.mesa.id = :mesaId AND r.id <> :excluirId " +
           "AND r.estado IN (com.nexora.restopos.model.ReservacionEstado.PENDIENTE, com.nexora.restopos.model.ReservacionEstado.CONFIRMADA) " +
           "AND r.fechaHora > :desde AND r.fechaHora < :hasta")
    List<Reservacion> choquesDeMesa(@Param("mesaId") Long mesaId, @Param("excluirId") Long excluirId,
                                    @Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    /** Activa (por confirmar o confirmada) de la misma persona ese día: por teléfono o por su cuenta. */
    @Query("SELECT r FROM Reservacion r WHERE r.tienda.id = :tiendaId AND r.estado IN :estados " +
           "AND r.fechaHora >= :desde AND r.fechaHora < :hasta " +
           "AND (r.telefono = :telefono OR (:clienteId IS NOT NULL AND r.cliente.id = :clienteId)) ORDER BY r.fechaHora ASC")
    List<Reservacion> activasDelDia(@Param("tiendaId") Long tiendaId, @Param("estados") Collection<ReservacionEstado> estados,
                                    @Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta,
                                    @Param("telefono") String telefono, @Param("clienteId") Long clienteId);

    /** Lo que hicieron los clientes con sus reservaciones desde {@code desde} (avisos del personal). */
    @Query("SELECT r FROM Reservacion r WHERE (:tiendaId IS NULL OR r.tienda.id = :tiendaId) " +
           "AND r.ultimoCambio IS NOT NULL AND r.modificadaAt >= :desde ORDER BY r.modificadaAt DESC")
    List<Reservacion> cambiosDeClientes(@Param("tiendaId") Long tiendaId, @Param("desde") LocalDateTime desde);
}
