package com.nexora.restopos.repository;

import com.nexora.restopos.model.Orden;
import com.nexora.restopos.model.OrdenEstado;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/** Repositorio de {@link Orden}. */
public interface OrdenRepository extends JpaRepository<Orden, Long> {

    /**
     * Búsqueda paginada de órdenes con filtros combinables.
     *
     * <p>{@code tiendaId} nulo indica SUPER_ADMIN viendo todas las tiendas; {@code estado}
     * nulo no filtra por estado. {@code from}/{@code to} siempre llegan con un valor real
     * desde el service (nunca {@code null}), igual que en el resto de los repositorios con
     * filtro de fecha.
     */
    // Las que esperan pago van primero (la caja cobra desde aquí con el ticket del cliente).
    @Query(value = "SELECT o FROM Orden o WHERE " +
           "(:tiendaId IS NULL OR o.tienda.id = :tiendaId) " +
           "AND o.openedAt BETWEEN :from AND :to " +
           "AND (:estado IS NULL OR o.estado = :estado) " +
           "ORDER BY CASE WHEN o.estado = com.nexora.restopos.model.OrdenEstado.PENDIENTE_PAGO THEN 0 ELSE 1 END, o.openedAt DESC",
           countQuery = "SELECT COUNT(o) FROM Orden o WHERE " +
           "(:tiendaId IS NULL OR o.tienda.id = :tiendaId) " +
           "AND o.openedAt BETWEEN :from AND :to " +
           "AND (:estado IS NULL OR o.estado = :estado)")
    Page<Orden> search(@Param("tiendaId") Long tiendaId,
                        @Param("from") LocalDateTime from,
                        @Param("to") LocalDateTime to,
                        @Param("estado") OrdenEstado estado,
                        Pageable pageable);

    /**
     * Órdenes activas (ni pagadas ni canceladas) para la pantalla de Cocina, más antiguas
     * primero — así el cocinero ve las comandas en el mismo orden en que se fueron pidiendo.
     * {@code mesaId} nulo no filtra por mesa (incluye también las órdenes para llevar).
     */
    @Query("SELECT o FROM Orden o WHERE " +
           "(:tiendaId IS NULL OR o.tienda.id = :tiendaId) " +
           "AND o.estado NOT IN (com.nexora.restopos.model.OrdenEstado.PAGADA, com.nexora.restopos.model.OrdenEstado.CANCELADA) " +
           "AND (:mesaId IS NULL OR o.mesa.id = :mesaId) " +
           "AND o.pendienteAceptar = false " +
           "ORDER BY o.openedAt ASC")
    List<Orden> findActiveForKitchen(@Param("tiendaId") Long tiendaId, @Param("mesaId") Long mesaId);

    /**
     * Órdenes para llevar activas — ni pagadas ni canceladas —, más antiguas primero, para el
     * tablero del módulo "Para llevar": las que no tienen mesa y las de "Comer aquí" (se piden
     * en caja y se les asigna mesa, así que salen también en Mesas).
     */
    @Query("SELECT o FROM Orden o WHERE " +
           "(:tiendaId IS NULL OR o.tienda.id = :tiendaId) " +
           "AND (o.mesa IS NULL OR o.tipoEntrega = 'COMER_AQUI') " +
           "AND o.estado NOT IN (com.nexora.restopos.model.OrdenEstado.PAGADA, com.nexora.restopos.model.OrdenEstado.CANCELADA) " +
           "AND (o.tipoEntrega IS NULL OR o.tipoEntrega <> 'RESERVACION') " +
           "ORDER BY o.openedAt ASC")
    List<Orden> findActiveTakeaway(@Param("tiendaId") Long tiendaId);

    /**
     * Órdenes activas (ni pagadas ni canceladas) de un mesero con al menos un producto que
     * cocina ya le entregó (ENTREGADO_MESERO) y él todavía no lleva a la mesa — base del aviso
     * "platos por recoger" (ver OrdenService#findPorRecoger). Más antiguas primero.
     */
    @Query("SELECT DISTINCT o FROM Orden o JOIN o.items i WHERE " +
           "(:tiendaId IS NULL OR o.tienda.id = :tiendaId) " +
           "AND o.mesero.id = :meseroId " +
           // Los pedidos de la página web no los lleva un mesero (los recoge el cliente o el
           // repartidor, que ve "Listo en cocina" en Entregas): aunque queden a nombre de quien
           // los aceptó, no deben avisarle como "platos por recoger".
           "AND o.origen <> 'WEB' " +
           "AND o.estado NOT IN (com.nexora.restopos.model.OrdenEstado.PAGADA, com.nexora.restopos.model.OrdenEstado.CANCELADA) " +
           "AND i.estado = com.nexora.restopos.model.OrdenDetalleEstado.ENTREGADO_MESERO " +
           "ORDER BY o.openedAt ASC")
    List<Orden> findActiveConPorRecoger(@Param("tiendaId") Long tiendaId, @Param("meseroId") Long meseroId);

    /**
     * Envíos a domicilio pendientes de entregar para la pantalla Entregas: ya aceptados, no
     * cancelados y sin entregar. Incluye los ya pagados (ej. transferencia confirmada en
     * caja) solo si son de las últimas 24 h ({@code pagadasDesde}), para que uno cobrado de
     * antemano no se quede fuera de la lista ni se cuelen pedidos viejos de antes de esta función.
     */
    @Query("SELECT o FROM Orden o WHERE " +
           "(:tiendaId IS NULL OR o.tienda.id = :tiendaId) " +
           "AND o.tipoEntrega = 'DOMICILIO' " +
           "AND o.pendienteAceptar = false " +
           "AND o.entregadaAt IS NULL " +
           "AND o.estado <> com.nexora.restopos.model.OrdenEstado.CANCELADA " +
           "AND (o.estado <> com.nexora.restopos.model.OrdenEstado.PAGADA OR o.openedAt >= :pagadasDesde) " +
           "ORDER BY o.openedAt ASC")
    List<Orden> findParaEntregar(@Param("tiendaId") Long tiendaId, @Param("pagadasDesde") LocalDateTime pagadasDesde);

    /**
     * Órdenes (de cualquier estado salvo canceladas, ya cobradas incluidas) con algún producto
     * que cocina entregó entre {@code desde} y {@code hasta}. Para lo entregado antes de que
     * existiera {@code entregadoCocinaAt} se toma la fecha de apertura de la orden.
     */
    @Query("SELECT DISTINCT o FROM Orden o JOIN o.items i WHERE " +
           "(:tiendaId IS NULL OR o.tienda.id = :tiendaId) " +
           "AND o.estado <> com.nexora.restopos.model.OrdenEstado.CANCELADA " +
           "AND i.estado IN (com.nexora.restopos.model.OrdenDetalleEstado.ENTREGADO_MESERO, com.nexora.restopos.model.OrdenDetalleEstado.ENTREGADO) " +
           "AND i.cargoExtra = false " +
           "AND ((i.entregadoCocinaAt IS NOT NULL AND i.entregadoCocinaAt BETWEEN :desde AND :hasta) " +
           "  OR (i.entregadoCocinaAt IS NULL AND o.openedAt BETWEEN :desde AND :hasta))")
    List<Orden> findConEntregadosCocinaEntre(@Param("tiendaId") Long tiendaId,
                                              @Param("desde") LocalDateTime desde,
                                              @Param("hasta") LocalDateTime hasta);

    /** Pedidos web con reseña del cliente en un rango (pantalla "Opiniones"). */
    @Query("SELECT o FROM Orden o WHERE (:tiendaId IS NULL OR o.tienda.id = :tiendaId) " +
           "AND o.resenaEstrellas IS NOT NULL AND o.resenaAt BETWEEN :desde AND :hasta")
    List<Orden> findConResenaEntre(@Param("tiendaId") Long tiendaId, @Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    /** Historial de pedidos de un cliente registrado, más recientes primero. */
    List<Orden> findTop50ByClienteIdOrderByOpenedAtDesc(Long clienteId);

    /** Pedidos de un cliente registrado en un rango de fechas (filtro de "Mis pedidos"). */
    List<Orden> findByClienteIdAndOpenedAtBetweenOrderByOpenedAtDesc(Long clienteId, LocalDateTime desde, LocalDateTime hasta);

    /** Pedido web ya creado con esa llave de idempotencia (envío repetido). */
    java.util.Optional<Orden> findByIdempotencyKey(String idempotencyKey);

    /** Pedido web por su código de seguimiento (ver Orden.trackingToken). */
    java.util.Optional<Orden> findByTrackingToken(String trackingToken);

    /** Pedidos de la página web que el personal todavía no acepta ni rechaza, más antiguos primero. */
    @Query("SELECT o FROM Orden o WHERE " +
           "(:tiendaId IS NULL OR o.tienda.id = :tiendaId) " +
           "AND o.pendienteAceptar = true " +
           "AND o.estado <> com.nexora.restopos.model.OrdenEstado.CANCELADA " +
           // Los pedidos anticipados de reservaciones esperan a que lleguen (no se aceptan aquí).
           "AND (o.tipoEntrega IS NULL OR o.tipoEntrega <> 'RESERVACION') " +
           "ORDER BY o.openedAt ASC")
    List<Orden> findPedidosWebPorAceptar(@Param("tiendaId") Long tiendaId);

    /** Pedidos con reporte del cliente "llegó mal" / "me faltó algo" desde {@code desde} (centro de notificaciones). */
    @Query("SELECT o FROM Orden o WHERE (:tiendaId IS NULL OR o.tienda.id = :tiendaId) AND o.reporteTipo IN ('MAL', 'FALTANTE') " +
           "AND o.reporteAt >= :desde ORDER BY o.reporteAt DESC")
    List<Orden> findReportesProblemaDesde(@Param("tiendaId") Long tiendaId, @Param("desde") java.time.LocalDateTime desde);

    /** Envíos a domicilio entregados desde {@code desde} (centro de notificaciones). */
    @Query("SELECT o FROM Orden o WHERE (:tiendaId IS NULL OR o.tienda.id = :tiendaId) AND o.entregadaAt >= :desde " +
           "ORDER BY o.entregadaAt DESC")
    List<Orden> findEntregadosDesde(@Param("tiendaId") Long tiendaId, @Param("desde") java.time.LocalDateTime desde);

    /** Envíos a domicilio entregados que la caja todavía no cobra (el dinero lo trae el repartidor). */
    @Query("SELECT o FROM Orden o WHERE (:tiendaId IS NULL OR o.tienda.id = :tiendaId) AND o.tipoEntrega = 'DOMICILIO' " +
           "AND o.entregadaAt IS NOT NULL AND o.estado <> com.nexora.restopos.model.OrdenEstado.PAGADA " +
           "AND o.estado <> com.nexora.restopos.model.OrdenEstado.CANCELADA ORDER BY o.entregadaAt ASC")
    List<Orden> findEntregadosPorCobrar(@Param("tiendaId") Long tiendaId);

    /** Historial de un repartidor: lo que entregó en el rango (más reciente primero). */
    @Query("SELECT o FROM Orden o WHERE o.repartidor.id = :repartidorId AND o.entregadaAt >= :desde AND o.entregadaAt < :hasta " +
           "ORDER BY o.entregadaAt DESC")
    List<Orden> findHistorialRepartidor(@Param("repartidorId") Long repartidorId,
                                        @Param("desde") java.time.LocalDateTime desde, @Param("hasta") java.time.LocalDateTime hasta);

    /** Envíos que un repartidor tomó desde {@code desde} (centro de notificaciones). */
    @Query("SELECT o FROM Orden o WHERE (:tiendaId IS NULL OR o.tienda.id = :tiendaId) AND o.tomadaAt >= :desde " +
           "ORDER BY o.tomadaAt DESC")
    List<Orden> findTomadosDesde(@Param("tiendaId") Long tiendaId, @Param("desde") java.time.LocalDateTime desde);

    /** Envíos que salieron en camino desde {@code desde} (centro de notificaciones). */
    @Query("SELECT o FROM Orden o WHERE (:tiendaId IS NULL OR o.tienda.id = :tiendaId) AND o.enCaminoAt >= :desde " +
           "ORDER BY o.enCaminoAt DESC")
    List<Orden> findEnCaminoDesde(@Param("tiendaId") Long tiendaId, @Param("desde") java.time.LocalDateTime desde);
}
