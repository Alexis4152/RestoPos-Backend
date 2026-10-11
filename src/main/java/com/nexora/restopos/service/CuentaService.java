package com.nexora.restopos.service;

import com.nexora.restopos.dto.CuentaCreateRequest;
import com.nexora.restopos.dto.CuentaItemRequest;
import com.nexora.restopos.dto.VentaResumen;
import com.nexora.restopos.model.*;
import com.nexora.restopos.repository.CuentaDetalleRepository;
import com.nexora.restopos.repository.CuentaRepository;
import com.nexora.restopos.repository.OrdenDetalleRepository;
import com.nexora.restopos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Crea y cancela {@link Cuenta}s: agrupa un subconjunto de los {@link OrdenDetalle} pendientes
 * de una {@link Orden} (total, por comensal, o una selección personalizada) para cobrarse.
 *
 * <p>La garantía de "nunca cobrar dos veces" descansa en {@code OrdenDetalle.cantidadFacturada}
 * + su {@code @Version}: cada línea que se incluye en una cuenta incrementa su cantidad
 * facturada dentro de la misma transacción, y si dos peticiones intentan facturar la misma
 * línea al mismo tiempo, Hibernate rechaza la segunda escritura con un error de bloqueo
 * optimista en vez de dejar pasar ambas.</p>
 */
@Service
@RequiredArgsConstructor
public class CuentaService {

    private final CuentaRepository cuentaRepository;
    private final CuentaDetalleRepository cuentaDetalleRepository;
    private final OrdenDetalleRepository ordenDetalleRepository;
    private final OrdenService ordenService;
    private final TenantScope tenantScope;
    private final AuditoriaService auditoriaService;

    public Cuenta findById(Long id) {
        return cuentaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cuenta no encontrada: " + id));
    }

    public Cuenta findById(Long id, User actor) {
        Cuenta c = findById(id);
        Long scope = tenantScope.scopeId(actor);
        if (scope != null && (c.getOrden().getTienda() == null || !scope.equals(c.getOrden().getTienda().getId()))) {
            throw new IllegalArgumentException("Cuenta no encontrada: " + id);
        }
        return c;
    }

    public List<Cuenta> findAllByOrden(Long ordenId, User actor) {
        ordenService.findById(ordenId, actor); // valida acceso a la orden
        return cuentaRepository.findByOrdenIdOrderByCreatedAtAsc(ordenId);
    }

    // BETWEEN siempre necesita las dos fechas: Postgres no logra inferir el tipo de un
    // parámetro timestamp nulo (mismo caso que en UserService/CashCutService.findAll).
    private static final LocalDateTime MIN_DATE = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final LocalDateTime MAX_DATE = LocalDateTime.of(2100, 1, 1, 0, 0);

    /**
     * Búsqueda paginada de cuentas (ventas) para el módulo de Ventas: qué se ha vendido,
     * filtrable por rango de fecha, estado (pendiente/pagada/cancelada) y método de pago,
     * acotado a la tienda del actor (o todas, si es SUPER_ADMIN sin tienda elegida).
     *
     * @param from fecha mínima de creación de la cuenta (inclusiva), o null para sin límite inferior
     * @param to fecha máxima de creación de la cuenta (inclusiva), o null para sin límite superior
     * @param estado filtro por estado de la cuenta, o null para no filtrar
     * @param metodo filtro por método de pago (de cualquiera de sus pagos no cancelados), o null para no filtrar
     * @param pageable paginación/orden solicitados
     * @param actor usuario que realiza la consulta; acota el resultado a su tienda
     * @return página de cuentas que cumplen los filtros
     */
    public Page<Cuenta> search(LocalDateTime from, LocalDateTime to, CuentaEstado estado, PaymentMethod metodo,
                                Pageable pageable, User actor) {
        LocalDateTime effectiveFrom = from != null ? from : MIN_DATE;
        LocalDateTime effectiveTo = to != null ? to : MAX_DATE;
        return cuentaRepository.search(tenantScope.scopeId(actor), effectiveFrom, effectiveTo, estado, metodo, pageable);
    }

    /**
     * Búsqueda paginada para el módulo de Ventas, agrupada por {@link Orden}: una orden puede
     * haberse cobrado con una mezcla de cuentas (Total, Por comensal, Personalizada), y aquí se
     * resume en un solo {@link VentaResumen} por orden en vez de una fila por cada cuenta suelta.
     * <p>
     * El filtro de fecha/método se resuelve en SQL (ver {@link CuentaRepository#searchOrdenIds}),
     * pero el de {@code estado} es una propiedad AGREGADA (depende de todas las cuentas de la
     * orden a la vez, no de una sola) y por eso se calcula y filtra en Java después de cargar el
     * desglose completo de cada orden candidata — ver {@link #calcularEstadoAgregado}.
     *
     * @param from fecha mínima de creación de alguna cuenta de la orden, o null para sin límite
     * @param to fecha máxima de creación de alguna cuenta de la orden, o null para sin límite
     * @param estado filtro por el estado agregado de la orden, o null para no filtrar
     * @param metodo filtro por método de pago (de cualquier cuenta/pago no cancelado de la orden), o null
     * @param pageable paginación solicitada (la página se arma en memoria, ver nota de arriba)
     * @param actor usuario que realiza la consulta; acota el resultado a su tienda
     */
    public Page<VentaResumen> searchVentas(LocalDateTime from, LocalDateTime to, CuentaEstado estado, PaymentMethod metodo,
                                            Pageable pageable, User actor) {
        LocalDateTime effectiveFrom = from != null ? from : MIN_DATE;
        LocalDateTime effectiveTo = to != null ? to : MAX_DATE;
        Long tiendaId = tenantScope.scopeId(actor);

        List<Long> ordenIds = cuentaRepository.searchOrdenIds(tiendaId, effectiveFrom, effectiveTo, metodo);
        if (ordenIds.isEmpty()) return new PageImpl<>(List.of(), pageable, 0);

        Map<Long, List<Cuenta>> cuentasPorOrden = cuentaRepository.findByOrdenIdInOrderByCreatedAtAsc(ordenIds).stream()
                .collect(Collectors.groupingBy(c -> c.getOrden().getId()));

        List<VentaResumen> resumenes = ordenIds.stream()
                .map(id -> buildResumen(id, cuentasPorOrden.get(id)))
                .filter(r -> estado == null || r.getEstado() == estado)
                .sorted(Comparator.comparing(VentaResumen::getFecha).reversed())
                .toList();

        int start = Math.min((int) pageable.getOffset(), resumenes.size());
        int end = Math.min(start + pageable.getPageSize(), resumenes.size());
        return new PageImpl<>(resumenes.subList(start, end), pageable, resumenes.size());
    }

    /** Arma el resumen de una orden a partir de todas sus cuentas ya cargadas (nunca vacías: viene de {@code searchOrdenIds}). */
    private VentaResumen buildResumen(Long ordenId, List<Cuenta> cuentas) {
        Orden orden = cuentas.get(0).getOrden();
        BigDecimal total = cuentas.stream()
                .filter(c -> c.getEstado() != CuentaEstado.CANCELADA)
                .map(Cuenta::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDateTime fecha = cuentas.stream().map(Cuenta::getCreatedAt).max(Comparator.naturalOrder()).orElse(orden.getOpenedAt());
        return VentaResumen.builder()
                .ordenId(ordenId)
                .mesa(orden.getMesa())
                .mesero(orden.getMesero())
                .fecha(fecha)
                .total(total)
                .estado(calcularEstadoAgregado(cuentas))
                .cuentas(cuentas)
                .build();
    }

    /**
     * Estado agregado de una orden a partir de sus cuentas: PAGADA solo si TODAS las cuentas no
     * canceladas ya se cobraron; CANCELADA si TODAS sus cuentas están canceladas; PENDIENTE en
     * cualquier otro caso, incluida una mezcla de cuentas pagadas y pendientes — se prefirió no
     * inventar un tercer estado "parcial" para no tener que enseñarle uno nuevo al cajero.
     */
    private CuentaEstado calcularEstadoAgregado(List<Cuenta> cuentas) {
        List<Cuenta> activas = cuentas.stream().filter(c -> c.getEstado() != CuentaEstado.CANCELADA).toList();
        if (activas.isEmpty()) return CuentaEstado.CANCELADA;
        boolean todasPagadas = activas.stream().allMatch(c -> c.getEstado() == CuentaEstado.PAGADA);
        return todasPagadas ? CuentaEstado.PAGADA : CuentaEstado.PENDIENTE;
    }

    /** Detalle de una "venta" (orden) para el módulo de Ventas — mismo resumen que arma {@link #searchVentas}, para una sola orden. */
    public VentaResumen findVentaByOrdenId(Long ordenId, User actor) {
        Orden orden = ordenService.findById(ordenId, actor); // valida acceso a la orden
        List<Cuenta> cuentas = cuentaRepository.findByOrdenIdOrderByCreatedAtAsc(ordenId);
        if (cuentas.isEmpty()) throw new IllegalArgumentException("Esta orden no tiene ninguna cuenta generada");
        return buildResumen(orden.getId(), cuentas);
    }

    /** Usado por {@link PagoService} para persistir los cambios de totales/estado tras un pago. */
    public Cuenta save(Cuenta cuenta) {
        return cuentaRepository.save(cuenta);
    }

    /**
     * Generar cuenta desde el punto de venta (botón "Generar cuenta"). Un pedido de la página
     * web a domicilio no se cobra aquí: lo cobra el repartidor al entregarlo (DeliveryService,
     * que llama directo a {@link #crear}).
     */
    @Transactional
    public Cuenta crearEnCaja(Long ordenId, CuentaCreateRequest req, User actor) {
        Orden orden = ordenService.findById(ordenId, actor);
        if ("WEB".equals(orden.getOrigen()) && "DOMICILIO".equals(orden.getTipoEntrega())) {
            throw new IllegalStateException("Este pedido web a domicilio lo cobra el repartidor al entregarlo");
        }
        return crear(ordenId, req, actor);
    }

    @Transactional
    public Cuenta crear(Long ordenId, CuentaCreateRequest req, User actor) {
        try {
            return crearInternal(ordenId, req, actor);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new IllegalStateException("La orden se modificó al mismo tiempo, intenta de nuevo");
        }
    }

    private Cuenta crearInternal(Long ordenId, CuentaCreateRequest req, User actor) {
        Orden orden = ordenService.findById(ordenId, actor);
        if (orden.getEstado() == OrdenEstado.PAGADA || orden.getEstado() == OrdenEstado.CANCELADA) {
            throw new IllegalStateException("La orden ya está cerrada");
        }

        Comensal comensalFiltro = null;
        List<PorFacturar> aFacturar = new ArrayList<>();

        switch (req.getTipo()) {
            case TOTAL -> {
                for (OrdenDetalle item : orden.getItems()) {
                    BigDecimal restante = restante(item);
                    if (item.getEstado() != OrdenDetalleEstado.CANCELADO && restante.compareTo(BigDecimal.ZERO) > 0) {
                        aFacturar.add(new PorFacturar(item, restante));
                    }
                }
            }
            case POR_COMENSAL -> {
                if (req.getComensalId() == null) throw new IllegalArgumentException("Debes indicar el comensal");
                comensalFiltro = orden.getComensales().stream()
                        .filter(c -> c.getId().equals(req.getComensalId()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Comensal no pertenece a esta orden"));
                for (OrdenDetalle item : orden.getItems()) {
                    BigDecimal restante = restante(item);
                    boolean esDeEsteComensal = item.getComensal() != null && item.getComensal().getId().equals(req.getComensalId());
                    if (esDeEsteComensal && item.getEstado() != OrdenDetalleEstado.CANCELADO && restante.compareTo(BigDecimal.ZERO) > 0) {
                        aFacturar.add(new PorFacturar(item, restante));
                    }
                }
            }
            case PERSONALIZADA -> {
                if (req.getItems() == null || req.getItems().isEmpty()) {
                    throw new IllegalArgumentException("Debes seleccionar al menos un producto");
                }
                for (CuentaItemRequest ir : req.getItems()) {
                    OrdenDetalle item = ordenDetalleRepository.findById(ir.getOrdenDetalleId())
                            .orElseThrow(() -> new IllegalArgumentException("Producto de orden no encontrado: " + ir.getOrdenDetalleId()));
                    if (!item.getOrden().getId().equals(ordenId)) {
                        throw new IllegalArgumentException("Ese producto no pertenece a esta orden");
                    }
                    BigDecimal restante = restante(item);
                    if (ir.getCantidad().compareTo(restante) > 0) {
                        throw new IllegalStateException("Cantidad solicitada excede lo disponible para \"" + item.getProductoNombre() + "\"");
                    }
                    aFacturar.add(new PorFacturar(item, ir.getCantidad()));
                }
            }
        }

        if (aFacturar.isEmpty()) {
            throw new IllegalStateException("No hay productos pendientes de pago");
        }

        Cuenta cuenta = new Cuenta();
        cuenta.setOrden(orden);
        cuenta.setTipo(req.getTipo());
        cuenta.setComensal(comensalFiltro);
        cuenta.setCreatedBy(actor);

        BigDecimal total = BigDecimal.ZERO;
        for (PorFacturar pf : aFacturar) {
            OrdenDetalle item = pf.item;
            BigDecimal unitario = item.getSubtotal().divide(item.getCantidad(), 4, RoundingMode.HALF_UP);
            BigDecimal monto = unitario.multiply(pf.cantidad).setScale(2, RoundingMode.HALF_UP);

            item.setCantidadFacturada(item.getCantidadFacturada().add(pf.cantidad));
            ordenDetalleRepository.save(item); // dispara el chequeo de @Version

            CuentaDetalle cd = new CuentaDetalle();
            cd.setCuenta(cuenta);
            cd.setOrdenDetalle(item);
            cd.setCantidad(pf.cantidad);
            cd.setMonto(monto);
            cuenta.getItems().add(cd);
            total = total.add(monto);
        }
        cuenta.setTotal(total);

        Cuenta saved = cuentaRepository.save(cuenta);
        saved.setTicketFolio("C-" + saved.getId());
        saved = cuentaRepository.save(saved);

        ordenService.marcarPendienteDePago(orden);
        return saved;
    }

    @Transactional
    public void cancelar(Long cuentaId, String motivo, User actor) {
        Cuenta cuenta = findById(cuentaId, actor);
        if (cuenta.getEstado() != CuentaEstado.PENDIENTE) {
            throw new IllegalStateException("Solo se puede cancelar una cuenta pendiente (sin pagos)");
        }
        for (CuentaDetalle cd : cuenta.getItems()) {
            OrdenDetalle item = cd.getOrdenDetalle();
            item.setCantidadFacturada(item.getCantidadFacturada().subtract(cd.getCantidad()));
            ordenDetalleRepository.save(item);
        }
        cuenta.setEstado(CuentaEstado.CANCELADA);
        cuentaRepository.save(cuenta);
        ordenService.revertirPendienteDePagoSiCorresponde(cuenta.getOrden());
        auditoriaService.log(actor, "CANCELAR_CUENTA", "Cuenta", cuenta.getId(), CuentaEstado.PENDIENTE, CuentaEstado.CANCELADA, motivo);
    }

    private BigDecimal restante(OrdenDetalle item) {
        return item.getCantidad().subtract(item.getCantidadFacturada());
    }

    private record PorFacturar(OrdenDetalle item, BigDecimal cantidad) {
    }
}
