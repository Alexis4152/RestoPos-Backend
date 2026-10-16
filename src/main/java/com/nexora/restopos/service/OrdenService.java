package com.nexora.restopos.service;

import com.nexora.restopos.dto.ComensalRequest;
import com.nexora.restopos.dto.KitchenOrdenView;
import com.nexora.restopos.dto.OrdenCreateRequest;
import com.nexora.restopos.dto.OrdenItemRequest;
import com.nexora.restopos.dto.OrdenItemUpdateRequest;
import com.nexora.restopos.dto.PublicPedidoRequest;
import com.nexora.restopos.dto.PublicPedidoStatus;
import com.nexora.restopos.model.*;
import com.nexora.restopos.repository.ComensalRepository;
import com.nexora.restopos.repository.CuentaRepository;
import com.nexora.restopos.repository.MesaRepository;
import com.nexora.restopos.repository.ModificadorGrupoRepository;
import com.nexora.restopos.repository.ModificadorOpcionRepository;
import com.nexora.restopos.repository.OrdenDetalleModificadorRepository;
import com.nexora.restopos.repository.OrdenDetalleRepository;
import com.nexora.restopos.repository.OrdenRepository;
import com.nexora.restopos.repository.ProductRepository;
import com.nexora.restopos.repository.TiendaInfoRepository;
import com.nexora.restopos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ciclo de vida de una {@link Orden}: apertura sobre una {@link Mesa} (o para llevar), captura
 * de {@link OrdenDetalle} y {@link Comensal}es, y cancelación. El cobro (creación de
 * {@link Cuenta}/{@link Pago}) vive en {@link CuentaService}/{@link PagoService}, que llaman de
 * vuelta a {@link #marcarPagadaSiCorresponde} y {@link #marcarPendienteDePago} para mantener el
 * estado de la orden y de su mesa sincronizado con lo que ya se cobró.
 */
@Service
@RequiredArgsConstructor
public class OrdenService {

    private static final LocalDateTime MIN_DATE = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final LocalDateTime MAX_DATE = LocalDateTime.of(2100, 1, 1, 0, 0);

    private final OrdenRepository ordenRepository;
    private final ComensalRepository comensalRepository;
    private final OrdenDetalleRepository ordenDetalleRepository;
    private final MesaRepository mesaRepository;
    private final CuentaRepository cuentaRepository;
    private final ProductService productService;
    private final ModificadorGrupoRepository modificadorGrupoRepository;
    private final ModificadorOpcionRepository modificadorOpcionRepository;
    private final OrdenDetalleModificadorRepository ordenDetalleModificadorRepository;
    private final TiendaInfoRepository tiendaInfoRepository;
    private final ProductRepository productRepository;
    private final com.nexora.restopos.repository.ClienteRepository clienteRepository;
    private final TenantScope tenantScope;
    private final AuditoriaService auditoriaService;
    private final ImageStorageService imageStorageService;

    public Page<Orden> findAll(LocalDateTime from, LocalDateTime to, OrdenEstado estado, Pageable pageable, User actor) {
        LocalDateTime effFrom = from != null ? from : MIN_DATE;
        LocalDateTime effTo = to != null ? to : MAX_DATE;
        return ordenRepository.search(tenantScope.scopeId(actor), effFrom, effTo, estado, pageable);
    }

    /** Órdenes para llevar activas (ver {@link com.nexora.restopos.controller.OrdenController#takeaway}), más antiguas primero. */
    public List<Orden> findActiveTakeaway(User actor) {
        return ordenRepository.findActiveTakeaway(tenantScope.scopeId(actor));
    }

    /**
     * Órdenes activas para la pantalla de Cocina (ver {@link com.nexora.restopos.controller.KitchenController}),
     * más antiguas primero, con sus productos ordenados por cuándo se pidieron (más viejo
     * primero) y sin los cancelados — el cocinero no necesita ver algo que ya no se va a servir.
     * <p>
     * Devuelve un {@link KitchenOrdenView} (no la {@link Orden} tal cual) a propósito: su
     * colección {@code items} tiene {@code orphanRemoval=true}, así que filtrar/ordenar esa
     * lista EN EL SITIO (in-place) le haría creer a Hibernate que esas líneas se
     * desvincularon del padre y las BORRARÍA de la base de datos en el siguiente flush. Armar
     * una lista nueva (vía stream, sin tocar la del entity) evita ese riesgo por completo.
     *
     * @param mesaId filtro opcional por mesa; null incluye todas (para llevar incluido)
     * @param actor  usuario autenticado; acota el resultado a su tienda
     */
    public List<KitchenOrdenView> findActiveForKitchen(Long mesaId, User actor) {
        List<Orden> ordenes = ordenRepository.findActiveForKitchen(tenantScope.scopeId(actor), mesaId);
        return ordenes.stream().map(orden -> KitchenOrdenView.builder()
                .id(orden.getId())
                .mesa(orden.getMesa())
                .mesero(orden.getMesero())
                .openedAt(orden.getOpenedAt())
                .estado(orden.getEstado())
                .items(orden.getItems().stream()
                        .filter(item -> item.getEstado() != OrdenDetalleEstado.CANCELADO)
                        .filter(item -> !Boolean.TRUE.equals(item.getCargoExtra()))
                        .sorted(java.util.Comparator.comparing(OrdenDetalle::getCreatedAt))
                        .toList())
                .build())
                .toList();
    }

    /**
     * Platos que cocina ya le entregó al mesero (ENTREGADO_MESERO) y que él todavía no lleva a
     * la mesa / al cliente, de SUS órdenes activas — alimenta el aviso "platos por recoger" del
     * frontend. Misma vista {@link KitchenOrdenView} (copia nueva, nunca la lista del entity),
     * con los items ya filtrados a solo esos.
     */
    public List<KitchenOrdenView> findPorRecoger(User actor) {
        return ordenRepository.findActiveConPorRecoger(tenantScope.scopeId(actor), actor.getId()).stream()
                .map(orden -> KitchenOrdenView.builder()
                        .id(orden.getId())
                        .mesa(orden.getMesa())
                        .mesero(orden.getMesero())
                        .openedAt(orden.getOpenedAt())
                        .estado(orden.getEstado())
                        .items(orden.getItems().stream()
                                .filter(item -> item.getEstado() == OrdenDetalleEstado.ENTREGADO_MESERO)
                                .sorted(java.util.Comparator.comparing(OrdenDetalle::getCreatedAt))
                                .toList())
                        .build())
                .toList();
    }

    /** Agrega a la orden la línea de cargo "Envío a domicilio" (no recalcula totales). */
    private void agregarCargoEnvio(Orden orden, BigDecimal costo) {
        OrdenDetalle envio = new OrdenDetalle();
        envio.setOrden(orden);
        envio.setProductoNombre("Envío a domicilio");
        envio.setCantidad(BigDecimal.ONE);
        envio.setPrecioUnitario(costo);
        envio.setDescuento(BigDecimal.ZERO);
        envio.setSubtotal(costo);
        envio.setEstado(OrdenDetalleEstado.ENTREGADO);
        envio.setCargoExtra(true);
        orden.getItems().add(ordenDetalleRepository.save(envio));
    }

    // ── Pedidos de la página web pública ──────────────────────────────────────────────

    private static final Set<String> TIPOS_ENTREGA = Set.of("RECOGER", "DOMICILIO");
    private static final Set<String> METODOS_PAGO = Set.of("EFECTIVO", "TARJETA", "TRANSFERENCIA");

    /**
     * Crea una orden para llevar a partir de un pedido de la página web (sin sesión, la tienda
     * ya viene resuelta por el slug). Nace con {@code pendienteAceptar=true} y sin mesero:
     * cocina no la ve hasta que alguien del personal la acepta ({@link #aceptarPedidoWeb}).
     * Valida contra la configuración web de la tienda (tipo de entrega y forma de pago
     * habilitados) y que cada producto sea de ESA tienda y se pueda pedir ahora mismo.
     */
    @Transactional
    public Orden crearPedidoWeb(TiendaInfo info, PublicPedidoRequest req) {
        return crearPedidoWeb(info, req, null);
    }

    /** Igual, ligando el pedido a un cliente registrado (para su historial y reseña). */
    @Transactional
    public Orden crearPedidoWeb(TiendaInfo info, PublicPedidoRequest req, Cliente cliente) {
        return crearPedidoWeb(info, req, cliente, null);
    }

    /** Pedido ya creado con esa llave de idempotencia en esta tienda, si existe. */
    public java.util.Optional<Orden> pedidoPorIdempotencia(TiendaInfo info, String key) {
        if (key == null || key.isBlank()) return java.util.Optional.empty();
        return ordenRepository.findByIdempotencyKey(key)
                .filter(o -> o.getTienda() != null && o.getTienda().getId().equals(info.getTienda().getId()));
    }

    /**
     * Igual, con llave de idempotencia: si ya existe un pedido con esa llave se devuelve tal
     * cual (no se crea otro). Dos envíos exactamente simultáneos los frena el índice único de
     * la columna; ver PublicController#crearPedido.
     */
    @Transactional
    public Orden crearPedidoWeb(TiendaInfo info, PublicPedidoRequest req, Cliente cliente, String idempotencyKey) {
        java.util.Optional<Orden> previo = pedidoPorIdempotencia(info, idempotencyKey);
        if (previo.isPresent()) return previo.get();
        Tienda tienda = info.getTienda();
        String tipoEntrega = req.getTipoEntrega().trim().toUpperCase();
        String metodoPago = req.getMetodoPago().trim().toUpperCase();
        if (!TIPOS_ENTREGA.contains(tipoEntrega)) throw new IllegalArgumentException("Tipo de entrega no válido");
        if (!METODOS_PAGO.contains(metodoPago)) throw new IllegalArgumentException("Forma de pago no válida");
        boolean domicilio = "DOMICILIO".equals(tipoEntrega);
        if (domicilio && !Boolean.TRUE.equals(info.getWebDomicilioEnabled())) {
            throw new IllegalArgumentException("Esta tienda no tiene servicio a domicilio por ahora");
        }
        if (!domicilio && !Boolean.TRUE.equals(info.getWebRecogerEnabled())) {
            throw new IllegalArgumentException("Esta tienda no recibe pedidos para recoger por ahora");
        }
        boolean pagoPermitido = switch (metodoPago) {
            case "EFECTIVO" -> Boolean.TRUE.equals(info.getWebPagoEfectivo());
            case "TARJETA" -> Boolean.TRUE.equals(info.getWebPagoTarjeta());
            default -> Boolean.TRUE.equals(info.getWebPagoTransferencia());
        };
        if (!pagoPermitido) throw new IllegalArgumentException("Esa forma de pago no está disponible");
        String direccion = req.getDireccion() != null ? req.getDireccion().trim() : "";
        if (domicilio && direccion.isEmpty()) throw new IllegalArgumentException("La dirección de entrega es obligatoria");

        Orden orden = new Orden();
        orden.setTienda(tienda);
        orden.setNumeroComensales(1);
        orden.setNotas(req.getNotas() != null && !req.getNotas().isBlank() ? req.getNotas().trim() : null);
        orden.setDireccion(domicilio ? direccion : null);
        orden.setClienteNombre(req.getClienteNombre().trim());
        orden.setClienteTelefono(req.getClienteTelefono().trim());
        orden.setOrigen("WEB");
        orden.setPendienteAceptar(true);
        orden.setMetodoPago(metodoPago);
        orden.setTipoEntrega(tipoEntrega);
        orden.setTrackingToken(nuevoTrackingToken());
        orden.setIdempotencyKey(idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey : null);
        orden.setCliente(cliente);
        orden.setEstado(OrdenEstado.ABIERTA);
        Orden saved = ordenRepository.save(orden);

        Comensal comensal = new Comensal();
        comensal.setOrden(saved);
        comensal.setNumero(1);
        comensalRepository.save(comensal);

        for (PublicPedidoRequest.Item it : req.getItems()) {
            Product producto = productRepository.findById(it.getProductoId())
                    .filter(p -> p.getTienda() != null && p.getTienda().getId().equals(tienda.getId()))
                    .orElseThrow(() -> new IllegalArgumentException("Un producto de tu pedido ya no existe"));
            boolean categoriaOk = producto.getCategory() == null
                    || (Boolean.TRUE.equals(producto.getCategory().getIsActive()) && producto.getCategory().isAvailableNow());
            if (!Boolean.TRUE.equals(producto.getIsActive()) || !Boolean.TRUE.equals(producto.getDisponible()) || !categoriaOk) {
                throw new IllegalArgumentException("\"" + producto.getName() + "\" no está disponible en este momento");
            }
            String obs = it.getObservaciones() != null && !it.getObservaciones().isBlank() ? it.getObservaciones().trim() : null;
            crearLinea(saved, producto, null, BigDecimal.valueOf(it.getCantidad()), BigDecimal.ZERO, obs, it.getModificadorOpcionIds());
        }

        if (domicilio) {
            BigDecimal costo = costoEnvioDomicilio(tienda);
            if (costo != null) agregarCargoEnvio(saved, costo);
        }
        recomputeTotals(saved);
        return ordenRepository.save(saved);
    }

    /** Pedidos web de la tienda del actor que nadie ha aceptado ni rechazado todavía. */
    public List<Orden> findPedidosWebPorAceptar(User actor) {
        return ordenRepository.findPedidosWebPorAceptar(tenantScope.scopeId(actor));
    }

    /**
     * El personal acepta un pedido web: queda a su nombre (mesero) y a partir de aquí cocina
     * ya lo ve. Rechazarlo es simplemente cancelarlo con motivo ({@link #cancelar}).
     */
    @Transactional
    public Orden aceptarPedidoWeb(Long ordenId, User actor) {
        Orden orden = findById(ordenId, actor);
        if (orden.getEstado() == OrdenEstado.CANCELADA) throw new IllegalStateException("Este pedido ya fue rechazado");
        if (!Boolean.TRUE.equals(orden.getPendienteAceptar())) throw new IllegalStateException("Este pedido ya fue aceptado");
        orden.setPendienteAceptar(false);
        orden.setMesero(actor);
        boolean tienePlatillos = orden.getItems().stream()
                .anyMatch(i -> !Boolean.TRUE.equals(i.getCargoExtra()) && i.getEstado() != OrdenDetalleEstado.CANCELADO);
        if (tienePlatillos && orden.getEstado() == OrdenEstado.ABIERTA) orden.setEstado(OrdenEstado.EN_PROCESO);
        Orden saved = ordenRepository.save(orden);
        auditoriaService.log(actor, "ACEPTAR_PEDIDO_WEB", "Orden", saved.getId(), null, null, null);
        return saved;
    }

    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    /** 32 bytes aleatorios en base64url (43 caracteres): imposible de adivinar por fuerza bruta. */
    private static String nuevoTrackingToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Estado de un pedido web para el cliente que lo hizo, buscado por su código de seguimiento
     * aleatorio (no por id, que es consecutivo y se podría adivinar). Debe ser de ESA tienda.
     */
    public PublicPedidoStatus estadoPedidoWeb(TiendaInfo info, String trackingToken) {
        Orden orden = (trackingToken == null || trackingToken.isBlank()
                ? java.util.Optional.<Orden>empty()
                : ordenRepository.findByTrackingToken(trackingToken.trim()))
                .filter(o -> o.getTienda() != null && o.getTienda().getId().equals(info.getTienda().getId()))
                .filter(o -> "WEB".equals(o.getOrigen()))
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado"));
        return estadoParaCliente(orden);
    }

    /** Vista del pedido para el cliente (seguimiento por código o historial del registrado). */
    public PublicPedidoStatus estadoParaCliente(Orden orden) {
        List<OrdenDetalle> activos = orden.getItems().stream()
                .filter(i -> i.getEstado() != OrdenDetalleEstado.CANCELADO)
                .toList();
        boolean domicilio = "DOMICILIO".equals(orden.getTipoEntrega());
        // ¿Cocina ya terminó todos los platillos?
        boolean todoListo = activos.stream()
                .filter(i -> !Boolean.TRUE.equals(i.getCargoExtra()))
                .allMatch(i -> i.getEstado() == OrdenDetalleEstado.LISTO
                        || i.getEstado() == OrdenDetalleEstado.ENTREGADO_MESERO
                        || i.getEstado() == OrdenDetalleEstado.ENTREGADO);
        String estado;
        if (orden.getEstado() == OrdenEstado.CANCELADA) estado = "RECHAZADO";
        else if (Boolean.TRUE.equals(orden.getPendienteAceptar())) estado = "RECIBIDO";
        // A domicilio cuenta como entregado cuando el repartidor lo marca (puede pagarse antes);
        // para recoger, cuando se cobra en caja.
        else if (domicilio && orden.getEntregadaAt() != null) estado = "ENTREGADO";
        // "En camino" solo cuando el repartidor marcó "Voy en camino" (que exige que el
        // restaurante ya se lo haya entregado). Tomarlo ("Lo llevo yo") no cambia nada para el cliente.
        else if (domicilio && orden.getEnCaminoAt() != null) estado = "EN_CAMINO";
        else if (!domicilio && orden.getEstado() == OrdenEstado.PAGADA) estado = "ENTREGADO";
        else estado = todoListo ? "LISTO" : "ACEPTADO";
        List<PublicPedidoStatus.Linea> lineas = activos.stream()
                .sorted(java.util.Comparator.comparing(OrdenDetalle::getCreatedAt,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .map(i -> new PublicPedidoStatus.Linea(i.getId(), i.getProductoNombre(), i.getCantidad(), i.getSubtotal(),
                        i.getModificadores().stream().map(m -> m.getGrupoNombre() + ": " + m.getNombre()
                                // El cliente ve cuánto le costó cada extra (el subtotal de la línea ya lo incluye).
                                + (m.getPrecioExtra() != null && m.getPrecioExtra().signum() > 0
                                        ? String.format(" (+$%,.2f)", m.getPrecioExtra()) : "")).toList()))
                .toList();
        LocalDateTime entregadoEn = "ENTREGADO".equals(estado) ? momentoEntrega(orden) : null;
        LocalDateTime reportarHasta = entregadoEn != null ? entregadoEn.plusHours(HORAS_PARA_REPORTAR) : null;
        // Las órdenes de mesa no usan el reporte de entrega ni la reseña web: se opinan por
        // cuenta (OpinionService), desde el QR del ticket o desde "Mis pedidos".
        boolean enMesa = orden.getMesa() != null;
        boolean puedeReportar = !enMesa && reportarHasta != null && orden.getReporteTipo() == null
                && LocalDateTime.now().isBefore(reportarHasta);
        return new PublicPedidoStatus(orden.getId(), estado, enMesa ? "MESA" : orden.getTipoEntrega(), orden.getMetodoPago(),
                orden.getTotal(), orden.getOpenedAt(),
                "RECHAZADO".equals(estado) ? orden.getMotivoCancelacion() : null, lineas,
                orden.getResenaEstrellas(), orden.getResenaComentario(),
                entregadoEn, orden.getReporteTipo(), orden.getReporteProductos(), orden.getReporteComentario(),
                enMesa ? null : reportarHasta, puedeReportar, enMesa ? orden.getMesa().getNumero() : null,
                costoEnvio(orden), orden.getComprobantePath() != null, orden.getTransferenciaIncluyeEnvio());
    }

    /** Suma de los cargos de envío (no cancelados) del pedido; cero si no tiene. */
    public static BigDecimal costoEnvio(Orden orden) {
        return orden.getItems().stream()
                .filter(i -> Boolean.TRUE.equals(i.getCargoExtra()) && i.getEstado() != OrdenDetalleEstado.CANCELADO)
                .map(OrdenDetalle::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * El cliente sube (o reemplaza) la foto del comprobante de su transferencia desde el
     * seguimiento del pedido, y dice si la transferencia incluye el costo de envío. Solo para
     * pedidos web a domicilio que eligieron pagar por transferencia, aún sin entregar ni pagar.
     */
    @Transactional
    public PublicPedidoStatus subirComprobanteWeb(TiendaInfo info, String trackingToken,
                                                  org.springframework.web.multipart.MultipartFile file, boolean incluyeEnvio) {
        Orden orden = ordenRepository.findByTrackingToken(trackingToken == null ? "" : trackingToken.trim())
                .filter(o -> o.getTienda() != null && o.getTienda().getId().equals(info.getTienda().getId()))
                .filter(o -> "WEB".equals(o.getOrigen()))
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado"));
        return subirComprobante(orden, file, incluyeEnvio);
    }

    /** Lo mismo que {@link #subirComprobanteWeb}, desde "Mis pedidos" del cliente registrado (pedido propio). */
    @Transactional
    public PublicPedidoStatus subirComprobanteCliente(TiendaInfo info, Cliente cliente, Long ordenId,
                                                      org.springframework.web.multipart.MultipartFile file, boolean incluyeEnvio) {
        Orden orden = ordenRepository.findById(ordenId)
                .filter(o -> o.getCliente() != null && o.getCliente().getId().equals(cliente.getId()))
                .filter(o -> o.getTienda() != null && o.getTienda().getId().equals(info.getTienda().getId()))
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado"));
        return subirComprobante(orden, file, incluyeEnvio);
    }

    /**
     * Foto del comprobante de transferencia de una orden, para el personal de la tienda (el
     * acceso por sección/rol lo valida el controlador; aquí, que la orden sea de su tienda).
     */
    public java.nio.file.Path archivoComprobante(Long ordenId, User actor) {
        Orden orden = findById(ordenId, actor);
        java.nio.file.Path archivo = imageStorageService.leerPrivado(orden.getComprobantePath());
        if (archivo == null) throw new IllegalArgumentException("Este pedido no tiene comprobante");
        return archivo;
    }

    private PublicPedidoStatus subirComprobante(Orden orden, org.springframework.web.multipart.MultipartFile file, boolean incluyeEnvio) {
        if (!"DOMICILIO".equals(orden.getTipoEntrega()) || !"TRANSFERENCIA".equals(orden.getMetodoPago())) {
            throw new IllegalStateException("Este pedido no se paga por transferencia");
        }
        if (orden.getEstado() == OrdenEstado.CANCELADA) throw new IllegalStateException("Este pedido fue cancelado");
        if (orden.getEstado() == OrdenEstado.PAGADA || orden.getEntregadaAt() != null) {
            throw new IllegalStateException("Este pedido ya se pagó o se entregó");
        }
        // Carpeta privada: el comprobante solo lo ve el personal con sesión (ver archivoComprobante).
        String ruta = imageStorageService.guardarPrivado(file, "comprobantes", "orden-" + orden.getId(), "El comprobante");
        imageStorageService.borrarPrivado(orden.getComprobantePath());
        orden.setComprobantePath(ruta);
        orden.setComprobanteAt(LocalDateTime.now());
        orden.setTransferenciaIncluyeEnvio(incluyeEnvio || costoEnvio(orden).signum() == 0);
        return estadoParaCliente(ordenRepository.save(orden));
    }

    /** Plazo para que el cliente reporte cómo llegó su pedido, contado desde la entrega. */
    public static final int HORAS_PARA_REPORTAR = 24;

    /**
     * Cuándo recibió el cliente su pedido: a domicilio, cuando el repartidor lo marcó entregado;
     * para recoger, cuando se cobró en caja (cierre de la orden).
     */
    public static LocalDateTime momentoEntrega(Orden orden) {
        if (orden.getEntregadaAt() != null) return orden.getEntregadaAt();
        return orden.getClosedAt();
    }

    /** Costo de envío a domicilio configurado por el admin de la tienda, o null si no cobra envío. */
    public BigDecimal costoEnvioDomicilio(User actor) {
        return costoEnvioDomicilio(tenantScope.tiendaForWrite(actor));
    }

    private BigDecimal costoEnvioDomicilio(Tienda tienda) {
        if (tienda == null) return null;
        return tiendaInfoRepository.findByTiendaId(tienda.getId())
                .map(TiendaInfo::getCostoEnvioDomicilio)
                .filter(c -> c.compareTo(BigDecimal.ZERO) > 0)
                .orElse(null);
    }

    /**
     * Vista "Ya entregados" de Cocina: lo que cocina entregó HOY (aunque la orden ya se haya
     * cobrado), solo esos productos, de lo más reciente a lo más viejo.
     */
    @Transactional(readOnly = true)
    public List<KitchenOrdenView> findEntregadosHoy(User actor) {
        LocalDateTime desde = java.time.LocalDate.now().atStartOfDay();
        LocalDateTime hasta = desde.plusDays(1);
        java.util.function.Predicate<OrdenDetalle> deHoy = i -> {
            if (Boolean.TRUE.equals(i.getCargoExtra())) return false;
            if (i.getEstado() != OrdenDetalleEstado.ENTREGADO_MESERO && i.getEstado() != OrdenDetalleEstado.ENTREGADO) return false;
            LocalDateTime t = i.getEntregadoCocinaAt() != null ? i.getEntregadoCocinaAt() : i.getOrden().getOpenedAt();
            return t != null && !t.isBefore(desde) && t.isBefore(hasta);
        };
        java.util.function.Function<OrdenDetalle, LocalDateTime> horaEntrega =
                i -> i.getEntregadoCocinaAt() != null ? i.getEntregadoCocinaAt() : i.getOrden().getOpenedAt();
        return ordenRepository.findConEntregadosCocinaEntre(tenantScope.scopeId(actor), desde, hasta).stream()
                .map(orden -> KitchenOrdenView.builder()
                        .id(orden.getId())
                        .mesa(orden.getMesa())
                        .mesero(orden.getMesero())
                        .openedAt(orden.getOpenedAt())
                        .estado(orden.getEstado())
                        .items(orden.getItems().stream()
                                .filter(deHoy)
                                .sorted(java.util.Comparator.comparing(horaEntrega).reversed())
                                .toList())
                        .build())
                .filter(v -> !v.getItems().isEmpty())
                .sorted(java.util.Comparator.comparing((KitchenOrdenView v) -> horaEntrega.apply(v.getItems().get(0))).reversed())
                .toList();
    }

    /**
     * El mesero liga la orden a un cliente registrado de la página web, buscándolo por su
     * correo o su teléfono: así la visita aparece en su "Mis pedidos" y puede opinar desde ahí.
     */
    @Transactional
    public Orden vincularCliente(Long ordenId, String busqueda, User actor) {
        Orden orden = findById(ordenId, actor);
        if ("WEB".equals(orden.getOrigen())) throw new IllegalStateException("Los pedidos web ya tienen su cliente");
        String q = busqueda.trim();
        String digitos = q.replaceAll("\\D", "");
        Long tiendaId = orden.getTienda().getId();
        Cliente cliente = (q.contains("@")
                ? clienteRepository.findByTiendaIdAndEmailIgnoreCase(tiendaId, q).filter(c -> Boolean.TRUE.equals(c.getIsActive()))
                : clienteRepository.findAllByTiendaIdAndIsActiveTrue(tiendaId).stream()
                        .filter(c -> digitos.length() >= 10 && c.getTelefono().replaceAll("\\D", "").endsWith(digitos.substring(digitos.length() - 10)))
                        .findFirst())
                .orElseThrow(() -> new IllegalArgumentException("No hay ningún cliente registrado con ese " + (q.contains("@") ? "correo" : "teléfono")));
        orden.setCliente(cliente);
        Orden saved = ordenRepository.save(orden);
        auditoriaService.log(actor, "VINCULAR_CLIENTE_ORDEN", "Orden", saved.getId(), null, cliente.getEmail(), null);
        return saved;
    }

    @Transactional
    public Orden desvincularCliente(Long ordenId, User actor) {
        Orden orden = findById(ordenId, actor);
        if ("WEB".equals(orden.getOrigen())) throw new IllegalStateException("No se puede quitar el cliente de un pedido web");
        orden.setCliente(null);
        return ordenRepository.save(orden);
    }

    public Orden findById(Long id) {
        return ordenRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Orden no encontrada: " + id));
    }

    public Orden findById(Long id, User actor) {
        Orden o = findById(id);
        Long scope = tenantScope.scopeId(actor);
        if (scope != null && (o.getTienda() == null || !scope.equals(o.getTienda().getId()))) {
            throw new IllegalArgumentException("Orden no encontrada: " + id);
        }
        return o;
    }

    @Transactional
    public Orden abrir(OrdenCreateRequest req, User actor) {
        Mesa mesa = null;
        if (req.getMesaId() != null) {
            mesa = mesaRepository.findById(req.getMesaId())
                    .orElseThrow(() -> new IllegalArgumentException("Mesa no encontrada: " + req.getMesaId()));
            if (mesa.getEstado() != MesaEstado.DISPONIBLE && mesa.getEstado() != MesaEstado.RESERVADA) {
                throw new IllegalStateException("La mesa no está disponible");
            }
        }

        // tiendaForWrite (no actor.getTienda()): para un actor de plataforma (SUPER_ADMIN/
        // SUPERVISOR), actor.getTienda() siempre es null (no tienen tienda propia) — usarlo
        // directo dejaba la orden sin tienda asignada, invisible después para findById(id,
        // actor)/findAll (scopeId ya no la encuentra porque o.getTienda() es null pero el
        // scope del actor no lo es). Mismo bug ya corregido en Product/Category/ModificadorGrupo.
        Tienda tiendaOrden = tenantScope.tiendaForWrite(actor);
        if (tiendaOrden == null && tenantScope.isPlatformActor(actor)) {
            throw new IllegalStateException("Elige una tienda para poder abrir una orden");
        }

        // Para llevar: nombre y teléfono del cliente obligatorios (la dirección sigue opcional).
        String clienteNombre = req.getClienteNombre() != null ? req.getClienteNombre().trim() : "";
        String clienteTelefono = req.getClienteTelefono() != null ? req.getClienteTelefono().trim() : "";
        boolean domicilio = mesa == null && Boolean.TRUE.equals(req.getDomicilio());
        BigDecimal costoEnvio = null;
        if (mesa == null) {
            if (clienteNombre.isEmpty()) throw new IllegalArgumentException("El nombre del cliente es obligatorio");
            if (clienteTelefono.isEmpty()) throw new IllegalArgumentException("El teléfono del cliente es obligatorio");
            if (domicilio) {
                costoEnvio = costoEnvioDomicilio(tiendaOrden);
                if (costoEnvio == null) {
                    throw new IllegalStateException("El administrador no ha configurado el costo de envío a domicilio");
                }
            }
        }
        Orden orden = new Orden();
        orden.setTienda(tiendaOrden);
        orden.setMesa(mesa);
        orden.setMesero(actor);
        orden.setNumeroComensales(req.getNumeroComensales() != null ? req.getNumeroComensales() : 1);
        orden.setNotas(req.getNotas());
        // Solo tiene sentido sin mesa (para llevar) — una orden de mesa no se entrega a
        // ninguna dirección, así que se ignora si llega junto con un mesaId.
        orden.setDireccion(mesa == null && req.getDireccion() != null && !req.getDireccion().isBlank() ? req.getDireccion().trim() : null);
        orden.setClienteNombre(mesa == null ? clienteNombre : null);
        orden.setClienteTelefono(mesa == null ? clienteTelefono : null);
        // Para llevar con envío: aparece en la pantalla de Entregas para que un repartidor la lleve.
        orden.setTipoEntrega(mesa == null ? (domicilio ? "DOMICILIO" : "RECOGER") : null);
        orden.setEstado(OrdenEstado.ABIERTA);
        Orden saved = ordenRepository.save(orden);

        // El envío entra como una línea más (cargoExtra): así se cobra en la cuenta y sale en el
        // ticket sin tocar CuentaService; ya nace ENTREGADO para que nunca quede "pendiente".
        if (costoEnvio != null) {
            agregarCargoEnvio(saved, costoEnvio);
            recomputeTotals(saved);
            saved = ordenRepository.save(saved);
        }

        for (int i = 1; i <= saved.getNumeroComensales(); i++) {
            Comensal c = new Comensal();
            c.setOrden(saved);
            c.setNumero(i);
            comensalRepository.save(c);
        }

        if (mesa != null) {
            mesa.setEstado(MesaEstado.OCUPADA);
            mesa.setOrdenActiva(saved);
            mesaRepository.save(mesa);
        }
        return saved;
    }

    @Transactional
    public Comensal agregarComensal(Long ordenId, ComensalRequest req, User actor) {
        Orden orden = findById(ordenId, actor);
        ensureModificable(orden);
        // MAX(numero)+1, NO count()+1: tras eliminar un comensal (ver #eliminarComensal) la
        // cuenta de filas restantes ya no coincide con el número más alto usado — contar
        // otra vez produciría un "Comensal N" duplicado si quedó un hueco en medio.
        int siguiente = comensalRepository.findByOrdenIdOrderByNumeroAsc(ordenId).stream()
                .mapToInt(Comensal::getNumero).max().orElse(0) + 1;
        Comensal c = new Comensal();
        c.setOrden(orden);
        c.setNumero(siguiente);
        c.setNombre(req.getNombre());
        Comensal saved = comensalRepository.save(c);
        orden.setNumeroComensales(siguiente);
        ordenRepository.save(orden);
        return saved;
    }

    /**
     * Elimina un comensal de la orden — pensado para corregir un alta de más (ej. el mesero
     * tocó "+ Agregar" de sobra). Solo se permite si el comensal sigue "vacío": ningún
     * {@link OrdenDetalle} lo referencia todavía, ni activo ni cancelado — una vez que se le
     * pidió algo, borrarlo perdería esa asociación (y con ella, cualquier {@link Cuenta}
     * "por comensal" ya generada a su nombre), así que a partir de ahí ya no se puede quitar.
     *
     * @param ordenId  id de la orden
     * @param comensalId id del comensal a eliminar
     * @param actor    usuario que realiza la eliminación; debe tener acceso a la tienda de la orden
     * @throws IllegalArgumentException si el comensal no existe o no pertenece a esta orden
     * @throws IllegalStateException si la orden ya está cerrada, o si el comensal ya tiene
     *         algún producto asociado
     */
    @Transactional
    public void eliminarComensal(Long ordenId, Long comensalId, User actor) {
        Orden orden = findById(ordenId, actor);
        ensureModificable(orden);
        Comensal comensal = orden.getComensales().stream()
                .filter(c -> c.getId().equals(comensalId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("La orden no tiene ese comensal: " + comensalId));
        if (ordenDetalleRepository.existsByComensalId(comensalId)) {
            throw new IllegalStateException("No se puede quitar un comensal que ya tiene productos asociados");
        }
        // Orden.comensales es mappedBy + orphanRemoval=true: hay que quitarlo de la colección
        // del padre (no borrarlo directo por su propio repositorio) para que Hibernate lo
        // marque huérfano y lo elimine al hacer flush — si se llama comensalRepository.delete()
        // a secas, la colección de `orden` (todavía en el contexto de persistencia con este
        // comensal adentro) lo vuelve a insertar por el cascade=ALL al final de la transacción.
        orden.getComensales().remove(comensal);
    }

    @Transactional
    public OrdenDetalle agregarItem(Long ordenId, OrdenItemRequest req, User actor) {
        Orden orden = findById(ordenId, actor);
        ensureModificable(orden);

        Product producto = productService.findById(req.getProductoId(), actor);
        if (!Boolean.TRUE.equals(producto.getIsActive()) || !Boolean.TRUE.equals(producto.getDisponible())) {
            throw new IllegalStateException("Producto no disponible: " + producto.getName());
        }

        Comensal comensal = null;
        if (req.getComensalId() != null) {
            comensal = orden.getComensales().stream()
                    .filter(c -> c.getId().equals(req.getComensalId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Comensal no pertenece a esta orden"));
        }

        OrdenDetalle saved = crearLinea(orden, producto, comensal, req.getCantidad(),
                req.getDescuento() != null ? req.getDescuento() : BigDecimal.ZERO,
                req.getObservaciones(), req.getModificadorOpcionIds());
        if (orden.getEstado() == OrdenEstado.ABIERTA) orden.setEstado(OrdenEstado.EN_PROCESO);
        recomputeTotals(orden);
        ordenRepository.save(orden);
        return saved;
    }

    /**
     * Crea y guarda una línea de la orden (con sus modificadores ya validados) y la agrega a
     * {@code orden.items}. No recalcula totales: eso lo hace quien la llama. Compartido entre
     * el alta desde el POS ({@link #agregarItem}) y los pedidos de la página web
     * ({@link #crearPedidoWeb}).
     */
    private OrdenDetalle crearLinea(Orden orden, Product producto, Comensal comensal, BigDecimal cantidad,
                                    BigDecimal descuento, String observaciones, List<Long> modificadorOpcionIds) {
        List<ModificadorOpcion> opcionesElegidas = resolverModificadoresElegidos(producto, modificadorOpcionIds);
        BigDecimal extrasUnitario = opcionesElegidas.stream()
                .map(ModificadorOpcion::getPrecioExtra)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // El subtotal SÍ incluye los extras (es lo que de verdad se cobra); precioUnitario se
        // deja en el precio base del producto para que el ticket pueda desglosar cada extra por
        // separado en vez de fundirlo en un "precio por unidad" que no coincide con el del menú.
        BigDecimal subtotal = producto.getPrice().add(extrasUnitario).multiply(cantidad).subtract(descuento);

        OrdenDetalle item = new OrdenDetalle();
        item.setOrden(orden);
        item.setComensal(comensal);
        item.setProducto(producto);
        item.setProductoNombre(producto.getName());
        item.setCantidad(cantidad);
        item.setPrecioUnitario(producto.getPrice());
        item.setDescuento(descuento);
        item.setSubtotal(subtotal);
        item.setObservaciones(observaciones);
        OrdenDetalle saved = ordenDetalleRepository.save(item);

        for (ModificadorOpcion opcion : opcionesElegidas) {
            OrdenDetalleModificador m = new OrdenDetalleModificador();
            m.setOrdenDetalle(saved);
            m.setGrupoNombre(opcion.getGrupo().getNombre());
            m.setNombre(opcion.getNombre());
            m.setPrecioExtra(opcion.getPrecioExtra());
            ordenDetalleModificadorRepository.save(m);
            saved.getModificadores().add(m);
        }
        orden.getItems().add(saved);
        return saved;
    }

    /**
     * Valida y resuelve las {@link ModificadorOpcion} elegidas para un producto: deben existir,
     * estar activas, pertenecer a un grupo activo DE ESE producto, respetar que un grupo
     * {@code UNICO} reciba a lo más una opción, y que todo grupo {@code obligatorio} reciba al
     * menos una — antes de dejar agregar el producto a la orden.
     */
    private List<ModificadorOpcion> resolverModificadoresElegidos(Product producto, List<Long> modificadorOpcionIds) {
        List<Long> ids = modificadorOpcionIds != null ? modificadorOpcionIds : List.of();
        List<ModificadorOpcion> elegidas = ids.isEmpty() ? List.of() : modificadorOpcionRepository.findAllById(ids);
        if (elegidas.size() != ids.size()) {
            throw new IllegalArgumentException("Una o más opciones de modificador no existen");
        }

        List<ModificadorGrupo> grupos = modificadorGrupoRepository
                .findAllByProductoIdAndIsActiveTrueOrderByOrdenAscIdAsc(producto.getId());
        Set<Long> idsGruposValidos = grupos.stream().map(ModificadorGrupo::getId).collect(Collectors.toSet());
        for (ModificadorOpcion opcion : elegidas) {
            if (!Boolean.TRUE.equals(opcion.getIsActive()) || !idsGruposValidos.contains(opcion.getGrupo().getId())) {
                throw new IllegalArgumentException("La opción \"" + opcion.getNombre() + "\" no pertenece a este producto");
            }
        }

        Map<Long, List<ModificadorOpcion>> elegidasPorGrupo = elegidas.stream()
                .collect(Collectors.groupingBy(o -> o.getGrupo().getId()));
        for (ModificadorGrupo grupo : grupos) {
            List<ModificadorOpcion> deEsteGrupo = elegidasPorGrupo.getOrDefault(grupo.getId(), List.of());
            if (Boolean.TRUE.equals(grupo.getObligatorio()) && deEsteGrupo.isEmpty()) {
                throw new IllegalArgumentException("Debes elegir una opción de \"" + grupo.getNombre() + "\"");
            }
            if (grupo.getTipo() == ModificadorGrupoTipo.UNICO && deEsteGrupo.size() > 1) {
                throw new IllegalArgumentException("\"" + grupo.getNombre() + "\" solo admite una opción");
            }
        }
        return elegidas;
    }

    /**
     * Edita una línea ya agregada: comensal, cantidad, notas y modificadores (el producto no —
     * para eso se cancela y se agrega otro). Reglas:
     * <ul>
     *   <li>Lo ya incluido en una cuenta no se puede deshacer: la cantidad no baja de
     *       {@code cantidadFacturada}, y si ya se facturó algo no se tocan los modificadores
     *       (cambiarían el precio de lo ya cobrado).</li>
     *   <li>Si cocina ya lo entregó (ENTREGADO_MESERO/ENTREGADO) solo se puede reasignar el
     *       comensal o bajar la cantidad: lo que haya que preparar de nuevo va como producto nuevo,
     *       para que sí le aparezca a cocina como pendiente.</li>
     * </ul>
     */
    @Transactional
    public OrdenDetalle editarItem(Long ordenId, Long itemId, OrdenItemUpdateRequest req, User actor) {
        Orden orden = findById(ordenId, actor);
        ensureModificable(orden);
        OrdenDetalle item = findItem(orden, itemId);
        if (item.getEstado() == OrdenDetalleEstado.CANCELADO) {
            throw new IllegalStateException("No se puede editar un producto cancelado");
        }
        if (Boolean.TRUE.equals(item.getCargoExtra())) {
            throw new IllegalStateException("El cargo de envío no se edita: cancélalo si no aplica");
        }

        Comensal comensal = null;
        if (req.getComensalId() != null) {
            comensal = orden.getComensales().stream()
                    .filter(c -> c.getId().equals(req.getComensalId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Comensal no pertenece a esta orden"));
        }

        BigDecimal facturada = item.getCantidadFacturada();
        if (req.getCantidad().compareTo(facturada) < 0) {
            throw new IllegalStateException("La cantidad no puede ser menor a la ya incluida en una cuenta ("
                    + facturada.stripTrailingZeros().toPlainString() + ")");
        }

        String nuevasObs = req.getObservaciones() != null && !req.getObservaciones().isBlank()
                ? req.getObservaciones().trim() : null;
        boolean cambiaObs = !java.util.Objects.equals(nuevasObs, item.getObservaciones());
        boolean subeCantidad = req.getCantidad().compareTo(item.getCantidad()) > 0;
        boolean cambiaMods = req.getModificadorOpcionIds() != null;

        boolean yaEntregado = item.getEstado() == OrdenDetalleEstado.ENTREGADO_MESERO
                || item.getEstado() == OrdenDetalleEstado.ENTREGADO;
        if (yaEntregado && (subeCantidad || cambiaObs || cambiaMods)) {
            throw new IllegalStateException("Este producto ya salió de cocina: solo puedes cambiar el comensal o bajar la cantidad. "
                    + "Para pedir más, agrégalo como producto nuevo");
        }
        if (cambiaMods && item.getProducto() == null) {
            throw new IllegalStateException("No se pueden cambiar los modificadores: el producto ya no existe en el menú");
        }
        if (cambiaMods && facturada.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException("No se pueden cambiar los modificadores: el producto ya se incluyó en una cuenta");
        }

        String anterior = describirItem(item);

        BigDecimal extrasUnitario;
        if (cambiaMods) {
            List<ModificadorOpcion> opciones = resolverModificadoresElegidos(item.getProducto(), req.getModificadorOpcionIds());
            item.getModificadores().clear();
            for (ModificadorOpcion opcion : opciones) {
                OrdenDetalleModificador m = new OrdenDetalleModificador();
                m.setOrdenDetalle(item);
                m.setGrupoNombre(opcion.getGrupo().getNombre());
                m.setNombre(opcion.getNombre());
                m.setPrecioExtra(opcion.getPrecioExtra());
                item.getModificadores().add(m);
            }
        }
        extrasUnitario = item.getModificadores().stream()
                .map(OrdenDetalleModificador::getPrecioExtra)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        item.setComensal(comensal);
        item.setCantidad(req.getCantidad());
        item.setObservaciones(nuevasObs);
        item.setSubtotal(item.getPrecioUnitario().add(extrasUnitario).multiply(req.getCantidad()).subtract(item.getDescuento()));
        OrdenDetalle saved = ordenDetalleRepository.save(item);

        recomputeTotals(orden);
        ordenRepository.save(orden);
        auditoriaService.log(actor, "EDITAR_ITEM_ORDEN", "OrdenDetalle", saved.getId(), anterior, describirItem(saved),
                req.getMotivo() != null && !req.getMotivo().isBlank() ? req.getMotivo().trim() : null);
        return saved;
    }

    private String describirItem(OrdenDetalle item) {
        String mods = item.getModificadores().stream()
                .map(m -> m.getGrupoNombre() + " " + m.getNombre())
                .collect(Collectors.joining(", "));
        return item.getCantidad().stripTrailingZeros().toPlainString() + "x " + item.getProductoNombre()
                + (item.getComensal() != null ? " · comensal " + item.getComensal().getNumero() : " · compartido")
                + (mods.isEmpty() ? "" : " · " + mods)
                + (item.getObservaciones() != null ? " · nota: " + item.getObservaciones() : "");
    }

    /**
     * Orden del flujo de un producto: solo se avanza, nunca se regresa. CANCELADO no está aquí
     * a propósito: cancelar va por {@link #cancelarItem} (que valida facturación, motivo y deja
     * auditoría), nunca por un cambio de estado directo.
     */
    private static final List<OrdenDetalleEstado> FLUJO_ITEM = List.of(
            OrdenDetalleEstado.PENDIENTE, OrdenDetalleEstado.PREPARANDO, OrdenDetalleEstado.LISTO,
            OrdenDetalleEstado.ENTREGADO_MESERO, OrdenDetalleEstado.ENTREGADO);

    public OrdenDetalle actualizarEstadoItem(Long ordenId, Long itemId, OrdenDetalleEstado estado, User actor) {
        Orden orden = findById(ordenId, actor);
        OrdenDetalle item = findItem(orden, itemId);
        if (estado == OrdenDetalleEstado.CANCELADO) {
            throw new IllegalArgumentException("Para cancelar un producto usa la opción Cancelar");
        }
        if (orden.getEstado() == OrdenEstado.CANCELADA || item.getEstado() == OrdenDetalleEstado.CANCELADO) {
            throw new IllegalStateException("Este producto (o su orden) está cancelado");
        }
        if (Boolean.TRUE.equals(item.getCargoExtra())) {
            throw new IllegalStateException("El cargo de envío no tiene estado de preparación");
        }
        if (item.getEstado() == estado) return item;
        if (FLUJO_ITEM.indexOf(estado) < FLUJO_ITEM.indexOf(item.getEstado())) {
            throw new IllegalStateException("El producto ya está en \"" + item.getEstado() + "\": no se puede regresar a \"" + estado + "\"");
        }
        item.setEstado(estado);
        if ((estado == OrdenDetalleEstado.ENTREGADO_MESERO || estado == OrdenDetalleEstado.ENTREGADO)
                && item.getEntregadoCocinaAt() == null) {
            item.setEntregadoCocinaAt(LocalDateTime.now());
        }
        return ordenDetalleRepository.save(item);
    }

    @Transactional
    public void cancelarItem(Long ordenId, Long itemId, String motivo, User actor) {
        Orden orden = findById(ordenId, actor);
        OrdenDetalle item = findItem(orden, itemId);
        if (item.getCantidadFacturada().compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException("No se puede cancelar: ya se incluyó en una cuenta");
        }
        OrdenDetalleEstado anterior = item.getEstado();
        item.setEstado(OrdenDetalleEstado.CANCELADO);
        ordenDetalleRepository.save(item);
        recomputeTotals(orden);
        ordenRepository.save(orden);
        auditoriaService.log(actor, "CANCELAR_ITEM_ORDEN", "OrdenDetalle", item.getId(), anterior, item.getEstado(), motivo);
    }

    @Transactional
    public void cancelar(Long ordenId, String motivo, User actor) {
        Orden orden = findById(ordenId, actor);
        boolean tienePagos = cuentaRepository.findByOrdenIdOrderByCreatedAtAsc(ordenId).stream()
                .anyMatch(c -> c.getEstado() == CuentaEstado.PAGADA);
        if (tienePagos) {
            throw new IllegalStateException("No se puede cancelar: la orden ya tiene pagos registrados");
        }
        OrdenEstado anterior = orden.getEstado();
        orden.setEstado(OrdenEstado.CANCELADA);
        orden.setCancelledBy(actor);
        orden.setCancelledAt(LocalDateTime.now());
        orden.setMotivoCancelacion(motivo);
        liberarMesa(orden);
        ordenRepository.save(orden);
        auditoriaService.log(actor, "CANCELAR_ORDEN", "Orden", orden.getId(), anterior, orden.getEstado(), motivo);
    }

    /** Llamado por {@link CuentaService} cuando se crea una cuenta: la mesa pasa a "en espera de cobro". */
    public void marcarPendienteDePago(Orden orden) {
        if (orden.getMesa() != null && orden.getMesa().getEstado() == MesaEstado.OCUPADA) {
            Mesa mesa = orden.getMesa();
            mesa.setEstado(MesaEstado.PENDIENTE_PAGO);
            mesaRepository.save(mesa);
        }
        if (orden.getEstado() == OrdenEstado.EN_PROCESO || orden.getEstado() == OrdenEstado.LISTA) {
            orden.setEstado(OrdenEstado.PENDIENTE_PAGO);
            ordenRepository.save(orden);
        }
    }

    /** Llamado por {@link PagoService} tras cada pago: si ya no queda nada pendiente, cierra la orden y libera la mesa. */
    public void marcarPagadaSiCorresponde(Orden orden) {
        boolean todoFacturado = orden.getItems().stream()
                .filter(i -> i.getEstado() != OrdenDetalleEstado.CANCELADO)
                .allMatch(i -> i.getCantidadFacturada().compareTo(i.getCantidad()) >= 0);
        boolean todoPagado = cuentaRepository.findByOrdenIdOrderByCreatedAtAsc(orden.getId()).stream()
                .filter(c -> c.getEstado() != CuentaEstado.CANCELADA)
                .allMatch(c -> c.getEstado() == CuentaEstado.PAGADA);
        if (todoFacturado && todoPagado) {
            orden.setEstado(OrdenEstado.PAGADA);
            orden.setClosedAt(LocalDateTime.now());
            liberarMesa(orden);
            ordenRepository.save(orden);
        }
    }

    /** Llamado por {@link CuentaService} al cancelar una cuenta pendiente: si ya no queda ninguna cuenta pendiente, regresa la mesa a OCUPADA. */
    public void revertirPendienteDePagoSiCorresponde(Orden orden) {
        if (orden.getMesa() == null || orden.getMesa().getEstado() != MesaEstado.PENDIENTE_PAGO) return;
        boolean hayPendientes = cuentaRepository.findByOrdenIdOrderByCreatedAtAsc(orden.getId()).stream()
                .anyMatch(c -> c.getEstado() == CuentaEstado.PENDIENTE);
        if (!hayPendientes) {
            Mesa mesa = orden.getMesa();
            mesa.setEstado(MesaEstado.OCUPADA);
            mesaRepository.save(mesa);
            if (orden.getEstado() == OrdenEstado.PENDIENTE_PAGO) {
                orden.setEstado(OrdenEstado.EN_PROCESO);
                ordenRepository.save(orden);
            }
        }
    }

    private void liberarMesa(Orden orden) {
        if (orden.getMesa() == null) return;
        Mesa mesa = orden.getMesa();
        mesa.setEstado(MesaEstado.DISPONIBLE);
        mesa.setOrdenActiva(null);
        mesaRepository.save(mesa);
    }

    private void ensureModificable(Orden orden) {
        if (orden.getEstado() == OrdenEstado.PAGADA || orden.getEstado() == OrdenEstado.CANCELADA) {
            throw new IllegalStateException("La orden ya está cerrada");
        }
    }

    private OrdenDetalle findItem(Orden orden, Long itemId) {
        return orden.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("La orden no tiene ese producto: " + itemId));
    }

    private void recomputeTotals(Orden orden) {
        BigDecimal subtotal = orden.getItems().stream()
                .filter(i -> i.getEstado() != OrdenDetalleEstado.CANCELADO)
                .map(OrdenDetalle::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        orden.setSubtotal(subtotal);
        orden.setTotal(subtotal.subtract(orden.getDescuento()).add(orden.getImpuesto()));
    }
}
