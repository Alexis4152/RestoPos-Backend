package com.nexora.restopos.service;

import com.nexora.restopos.dto.CuentaCreateRequest;
import com.nexora.restopos.dto.EntregaRequest;
import com.nexora.restopos.dto.PagoRequest;
import com.nexora.restopos.model.*;
import com.nexora.restopos.repository.OrdenRepository;
import com.nexora.restopos.repository.TiendaInfoRepository;
import com.nexora.restopos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Pantalla "Entregas" del repartidor: envíos a domicilio pendientes, tomarlos ("lo llevo yo"),
 * soltarlos, y entregarlos cobrando lo que falte en efectivo o por transferencia a la cuenta
 * de la tienda. El cobro reutiliza el flujo normal (cuenta TOTAL + pago en el corte de caja
 * del repartidor, ver {@link PagoService#registrar}), así que entra a ventas y cortes igual que
 * cualquier cobro en caja.
 */
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final OrdenRepository ordenRepository;
    private final OrdenService ordenService;
    private final CuentaService cuentaService;
    private final PagoService pagoService;
    private final TiendaInfoRepository tiendaInfoRepository;
    private final TenantScope tenantScope;
    private final AuditoriaService auditoriaService;
    private final com.nexora.restopos.repository.EntregaEventoRepository entregaEventoRepository;
    private final com.nexora.restopos.repository.PagoRepository pagoRepository;

    /** Envíos a domicilio sin entregar de la tienda del actor (los tomados por otros incluidos). */
    public List<Orden> pendientes(User actor) {
        return ordenRepository.findParaEntregar(tenantScope.scopeId(actor), LocalDateTime.now().minusHours(24));
    }

    /** Datos bancarios de la tienda, para que el cliente transfiera al pagar al repartidor. */
    public Map<String, String> datosTransferencia(User actor) {
        Tienda tienda = tenantScope.tiendaForWrite(actor);
        TiendaInfo info = tienda == null ? null : tiendaInfoRepository.findByTiendaId(tienda.getId()).orElse(null);
        java.util.HashMap<String, String> out = new java.util.HashMap<>();
        if (info != null) {
            out.put("banco", info.getTransferenciaBanco());
            out.put("titular", info.getTransferenciaTitular());
            out.put("clabe", info.getTransferenciaClabe());
        }
        return out;
    }

    /** El repartidor toma un envío: queda a su nombre y el cliente lo ve "En camino". */
    @Transactional
    public Orden tomar(Long ordenId, User actor) {
        Orden orden = envioPendiente(ordenId, actor);
        if (orden.getRepartidor() != null && !orden.getRepartidor().getId().equals(actor.getId())) {
            throw new IllegalStateException("Este pedido ya lo lleva " + orden.getRepartidor().getName());
        }
        if (orden.getRepartidor() != null) return orden; // ya era suyo: no se repite en el histórico
        orden.setRepartidor(actor);
        orden.setTomadaAt(LocalDateTime.now());
        Orden saved = ordenRepository.save(orden);
        evento(saved, "TOMADO", actor, actor, null);
        auditoriaService.log(actor, "TOMAR_ENTREGA", "Orden", saved.getId(), null, actor.getName(), null);
        return saved;
    }

    /**
     * El repartidor sale con el pedido: solo si ya lo tomó y el restaurante ya se lo entregó
     * (todos los platillos en ENTREGADO, "Entregado para llevar" en el punto de venta). Desde
     * aquí el cliente ve "En camino".
     */
    @Transactional
    public Orden enCamino(Long ordenId, User actor) {
        Orden orden = envioPendiente(ordenId, actor);
        if (orden.getRepartidor() == null || !orden.getRepartidor().getId().equals(actor.getId())) {
            throw new IllegalStateException("Primero toma el pedido con \"Lo llevo yo\"");
        }
        if (orden.getEnCaminoAt() != null) return orden;
        if (!entregadoAlRepartidor(orden)) {
            throw new IllegalStateException("El restaurante todavía no te entrega el pedido");
        }
        orden.setEnCaminoAt(LocalDateTime.now());
        Orden saved = ordenRepository.save(orden);
        evento(saved, "EN_CAMINO", actor, actor, null);
        auditoriaService.log(actor, "EN_CAMINO_ENTREGA", "Orden", saved.getId(), null, actor.getName(), null);
        return saved;
    }

    /** ¿El restaurante ya entregó todos los platillos (no cancelados) para llevar? */
    private static boolean entregadoAlRepartidor(Orden orden) {
        return orden.getItems().stream()
                .filter(i -> !Boolean.TRUE.equals(i.getCargoExtra()) && i.getEstado() != OrdenDetalleEstado.CANCELADO)
                .allMatch(i -> i.getEstado() == OrdenDetalleEstado.ENTREGADO);
    }

    /**
     * Suelta un envío que había tomado, con el motivo (obligatorio). Se puede aunque ya vaya en
     * camino: el pedido vuelve a quedar disponible para otro repartidor, el cliente ve "estamos
     * buscando otro repartidor" y la caja recibe el aviso con el motivo. Un admin puede soltar
     * cualquiera.
     */
    @Transactional
    public Orden soltar(Long ordenId, String motivo, User actor) {
        String m = motivo == null ? "" : motivo.trim();
        if (m.length() < 3) throw new IllegalArgumentException("Escribe por qué sueltas el pedido");
        if (m.length() > 100) m = m.substring(0, 100);
        Orden orden = envioPendiente(ordenId, actor);
        if (orden.getRepartidor() == null) return orden;
        if (!orden.getRepartidor().getId().equals(actor.getId()) && !tenantScope.isAdminLevel(actor)) {
            throw new IllegalStateException("Solo quien lleva el pedido puede soltarlo");
        }
        User anteriorRepartidor = orden.getRepartidor();
        String anterior = anteriorRepartidor.getName();
        orden.setRepartidor(null);
        orden.setTomadaAt(null);
        orden.setEnCaminoAt(null); // ya no va en camino: lo tiene que tomar otro
        Orden saved = ordenRepository.save(orden);
        evento(saved, "SOLTADO", anteriorRepartidor, actor, m);
        auditoriaService.log(actor, "SOLTAR_ENTREGA", "Orden", saved.getId(), anterior, null, m);
        return saved;
    }

    /**
     * El repartidor entrega el pedido. NO maneja caja: si falta pagar, solo deja dicho cómo pagó
     * el cliente (efectivo, con cuánto, o transferencia) y el pedido queda "por recibir en caja"
     * a su nombre; la caja lo cobra en su propio corte cuando él le entrega el dinero
     * ({@link #recibirDinero}). Los platillos quedan ENTREGADO.
     */
    @Transactional
    public Orden entregar(Long ordenId, EntregaRequest req, User actor) {
        Orden orden = envioPendiente(ordenId, actor);
        if (orden.getRepartidor() != null && !orden.getRepartidor().getId().equals(actor.getId())
                && !tenantScope.isAdminLevel(actor)) {
            throw new IllegalStateException("Este pedido lo lleva " + orden.getRepartidor().getName());
        }
        // Primero "Voy en camino" (el cliente de la web debe ver esa etapa antes de "Entregado").
        if (orden.getEnCaminoAt() == null) {
            throw new IllegalStateException("Marca \"Voy en camino\" antes de entregar");
        }

        String detalle = "Ya estaba pagado";
        if (orden.getEstado() != OrdenEstado.PAGADA) {
            PaymentMethod metodo = req.getMetodo();
            if (metodo != PaymentMethod.CASH && metodo != PaymentMethod.TRANSFER) {
                throw new IllegalArgumentException("Elige cómo pagó el cliente: efectivo o transferencia");
            }
            BigDecimal saldo = saldoPendiente(orden, actor);
            if (metodo == PaymentMethod.CASH) {
                if (req.getRecibido() == null || req.getRecibido().compareTo(saldo) < 0) {
                    throw new IllegalArgumentException("Captura con cuánto pagó el cliente (mínimo " + saldo + ")");
                }
                orden.setMetodoPago("EFECTIVO");
                orden.setPagaCon(req.getRecibido());
                detalle = "Cobró " + saldo + " en efectivo (recibió " + req.getRecibido() + ")";
            } else {
                BigDecimal envioCash = envioEnEfectivo(orden, saldo);
                if ("WEB".equals(orden.getOrigen()) && orden.getComprobantePath() == null) {
                    throw new IllegalStateException("El cliente todavía no sube el comprobante de su transferencia; cóbrale en efectivo o espera a que lo suba");
                }
                if (envioCash.signum() > 0 && (req.getRecibido() == null || req.getRecibido().compareTo(envioCash) < 0)) {
                    throw new IllegalArgumentException("Cobra el envío en efectivo (" + envioCash + "): captura con cuánto pagó el cliente");
                }
                orden.setMetodoPago("TRANSFERENCIA");
                if (envioCash.signum() > 0) orden.setPagaCon(req.getRecibido());
                String ref = req.getReferencia() != null && !req.getReferencia().isBlank() ? " · folio " + req.getReferencia().trim() : "";
                detalle = "Pagó por transferencia" + ref + (envioCash.signum() > 0 ? " y el envío en efectivo" : "");
            }
            if (detalle.length() > 100) detalle = detalle.substring(0, 100);
            ordenRepository.save(orden);
        }

        for (OrdenDetalle item : orden.getItems()) {
            if (item.getEstado() != OrdenDetalleEstado.CANCELADO && item.getEstado() != OrdenDetalleEstado.ENTREGADO
                    && !Boolean.TRUE.equals(item.getCargoExtra())) {
                ordenService.actualizarEstadoItem(orden.getId(), item.getId(), OrdenDetalleEstado.ENTREGADO, actor);
            }
        }
        orden = ordenService.findById(ordenId, actor);
        if (orden.getRepartidor() == null) {
            orden.setRepartidor(actor);
            orden.setTomadaAt(LocalDateTime.now());
        }
        orden.setEntregadaAt(LocalDateTime.now());
        Orden saved = ordenRepository.save(orden);
        evento(saved, "ENTREGADO", saved.getRepartidor(), actor, detalle);
        auditoriaService.log(actor, "ENTREGAR_PEDIDO", "Orden", saved.getId(), null,
                req.getMetodo() != null ? req.getMetodo().name() : "YA_PAGADO", null);
        return saved;
    }

    /** Lo que falta pagar de la orden: lo no facturado más el saldo de sus cuentas pendientes. */
    private BigDecimal saldoPendiente(Orden orden, User actor) {
        BigDecimal sinFacturar = orden.getItems().stream()
                .filter(i -> i.getEstado() != OrdenDetalleEstado.CANCELADO)
                .map(i -> i.getSubtotal().divide(i.getCantidad(), 2, java.math.RoundingMode.HALF_UP)
                        .multiply(i.getCantidad().subtract(i.getCantidadFacturada())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal enCuentas = cuentaService.findAllByOrden(orden.getId(), actor).stream()
                .filter(c -> c.getEstado() == CuentaEstado.PENDIENTE)
                .map(c -> c.getTotal().subtract(c.getTotalPagado()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sinFacturar.add(enCuentas).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /** Transferencia web que el cliente dijo que NO incluye el envío: el envío se paga en efectivo. */
    private static BigDecimal envioEnEfectivo(Orden orden, BigDecimal saldo) {
        return "WEB".equals(orden.getOrigen()) && orden.getComprobantePath() != null
                && Boolean.FALSE.equals(orden.getTransferenciaIncluyeEnvio())
                ? OrdenService.costoEnvio(orden).min(saldo) : BigDecimal.ZERO;
    }

    /** Envíos entregados que la caja todavía no cobra (el dinero lo trae el repartidor). */
    public List<Orden> porRecibir(User actor) {
        return ordenRepository.findEntregadosPorCobrar(tenantScope.scopeId(actor));
    }

    /**
     * La caja recibe del repartidor el dinero de un envío entregado: lo cobra en SU corte de
     * caja (como cualquier cobro, exige que lo tenga abierto) con la forma en que pagó el
     * cliente, y la orden queda pagada.
     */
    @Transactional
    public Orden recibirDinero(Long ordenId, User actor) {
        Orden orden = ordenService.findById(ordenId, actor);
        if (!"DOMICILIO".equals(orden.getTipoEntrega()) || orden.getEntregadaAt() == null) {
            throw new IllegalStateException("Este pedido no es un envío entregado");
        }
        if (orden.getEstado() == OrdenEstado.PAGADA) throw new IllegalStateException("Este pedido ya está cobrado");
        if (orden.getEstado() == OrdenEstado.CANCELADA) throw new IllegalStateException("Este pedido fue cancelado");

        boolean faltaFacturar = orden.getItems().stream()
                .anyMatch(i -> i.getEstado() != OrdenDetalleEstado.CANCELADO
                        && i.getCantidadFacturada().compareTo(i.getCantidad()) < 0);
        if (faltaFacturar) {
            CuentaCreateRequest cr = new CuentaCreateRequest();
            cr.setTipo(CuentaTipo.TOTAL);
            cuentaService.crear(orden.getId(), cr, actor);
        }
        List<Cuenta> pendientes = cuentaService.findAllByOrden(orden.getId(), actor).stream()
                .filter(c -> c.getEstado() == CuentaEstado.PENDIENTE).toList();
        BigDecimal saldo = pendientes.stream()
                .map(c -> c.getTotal().subtract(c.getTotalPagado()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String repartidor = orden.getRepartidor() != null ? orden.getRepartidor().getName() : "repartidor";

        if ("TRANSFERENCIA".equals(orden.getMetodoPago())) {
            BigDecimal envioCash = envioEnEfectivo(orden, saldo);
            String ref = ("Entregado por " + repartidor + ("WEB".equals(orden.getOrigen()) ? " · comprobante web" : ""));
            pagarCuentas(pendientes, PaymentMethod.TRANSFER, saldo.subtract(envioCash), null, ref.length() > 100 ? ref.substring(0, 100) : ref, actor);
            if (envioCash.signum() > 0) {
                List<Cuenta> conSaldo = cuentaService.findAllByOrden(orden.getId(), actor).stream()
                        .filter(c -> c.getEstado() == CuentaEstado.PENDIENTE).toList();
                BigDecimal recibido = orden.getPagaCon() != null && orden.getPagaCon().compareTo(envioCash) >= 0 ? orden.getPagaCon() : envioCash;
                pagarCuentas(conSaldo, PaymentMethod.CASH, envioCash, recibido, null, actor);
            }
        } else {
            BigDecimal recibido = orden.getPagaCon() != null && orden.getPagaCon().compareTo(saldo) >= 0 ? orden.getPagaCon() : saldo;
            pagarCuentas(pendientes, PaymentMethod.CASH, saldo, recibido, "Entregado por " + repartidor, actor);
        }
        Orden saved = ordenService.findById(ordenId, actor);
        evento(saved, "RECIBIDO_CAJA", saved.getRepartidor(), actor, "Recibió " + actor.getName());
        auditoriaService.log(actor, "RECIBIR_DINERO_ENVIO", "Orden", saved.getId(), repartidor, actor.getName(), null);
        return saved;
    }

    /** Historial del repartidor: sus envíos entregados entre {@code desde} y {@code hasta} (inclusive). */
    public List<Orden> historial(User actor, java.time.LocalDate desde, java.time.LocalDate hasta) {
        java.time.LocalDate h = hasta != null ? hasta : java.time.LocalDate.now();
        java.time.LocalDate d = desde != null ? desde : h.minusDays(6);
        return ordenRepository.findHistorialRepartidor(actor.getId(), d.atStartOfDay(), h.plusDays(1).atStartOfDay());
    }

    /**
     * Reparte {@code monto} entre las cuentas pendientes (en orden) con un mismo método. El
     * efectivo recibido (para el cambio) se registra una sola vez, en el primer pago.
     */
    private void pagarCuentas(List<Cuenta> cuentas, PaymentMethod metodo, BigDecimal monto,
                              BigDecimal recibido, String referencia, User actor) {
        BigDecimal restante = monto;
        boolean primero = true;
        for (Cuenta c : cuentas) {
            if (restante.signum() <= 0) break;
            BigDecimal saldoCuenta = c.getTotal().subtract(c.getTotalPagado());
            if (saldoCuenta.signum() <= 0) continue;
            BigDecimal parte = saldoCuenta.min(restante);
            PagoRequest pago = new PagoRequest();
            pago.setMetodo(metodo);
            pago.setMonto(parte);
            if (metodo == PaymentMethod.CASH && primero) pago.setRecibido(recibido);
            pago.setReferencia(referencia);
            pagoService.registrar(c.getId(), pago, actor);
            restante = restante.subtract(parte);
            primero = false;
        }
    }

    private void evento(Orden orden, String tipo, User repartidor, User actor, String detalle) {
        entregaEventoRepository.save(EntregaEvento.builder()
                .ordenId(orden.getId()).tipo(tipo).repartidor(repartidor).actor(actor).detalle(detalle).build());
    }

    /**
     * Seguimiento del envío para el punto de venta: estado actual y el histórico de pasos del
     * repartidor. Para envíos anteriores a que existiera el histórico, se arma con las horas
     * guardadas en la orden.
     */
    public com.nexora.restopos.dto.EntregaSeguimiento seguimiento(Long ordenId, User actor) {
        Orden orden = ordenService.findById(ordenId, actor);
        if (!"DOMICILIO".equals(orden.getTipoEntrega())) throw new IllegalArgumentException("Este pedido no es a domicilio");
        List<com.nexora.restopos.dto.EntregaSeguimiento.Evento> eventos = entregaEventoRepository
                .findByOrdenIdOrderByCreatedAtAscIdAsc(ordenId).stream()
                .map(e -> new com.nexora.restopos.dto.EntregaSeguimiento.Evento(e.getTipo(),
                        e.getRepartidor() != null ? e.getRepartidor().getName() : null,
                        e.getActor() != null && (e.getRepartidor() == null || !e.getActor().getId().equals(e.getRepartidor().getId()))
                                ? e.getActor().getName() : null,
                        e.getDetalle(), e.getCreatedAt()))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        String nombre = orden.getRepartidor() != null ? orden.getRepartidor().getName() : null;
        if (eventos.isEmpty()) {
            if (orden.getTomadaAt() != null) eventos.add(new com.nexora.restopos.dto.EntregaSeguimiento.Evento("TOMADO", nombre, null, null, orden.getTomadaAt()));
            if (orden.getEnCaminoAt() != null) eventos.add(new com.nexora.restopos.dto.EntregaSeguimiento.Evento("EN_CAMINO", nombre, null, null, orden.getEnCaminoAt()));
            if (orden.getEntregadaAt() != null) eventos.add(new com.nexora.restopos.dto.EntregaSeguimiento.Evento("ENTREGADO", nombre, null, null, orden.getEntregadaAt()));
        }
        String estado;
        if (orden.getEstado() == OrdenEstado.CANCELADA) estado = "CANCELADO";
        else if (orden.getEntregadaAt() != null) estado = "ENTREGADO";
        else if (orden.getEnCaminoAt() != null) estado = "EN_CAMINO";
        else if (orden.getRepartidor() != null) estado = "TOMADO";
        else estado = "SIN_REPARTIDOR";
        return new com.nexora.restopos.dto.EntregaSeguimiento(estado, nombre, orden.getTomadaAt(),
                orden.getEnCaminoAt(), orden.getEntregadaAt(), eventos, cobro(orden));
    }

    private com.nexora.restopos.dto.EntregaSeguimiento.Cobro cobro(Orden orden) {
        List<Pago> pagos = pagoRepository.findByCuentaOrdenIdAndCanceladoFalseOrderByCreatedAtAsc(orden.getId());
        BigDecimal total = orden.getTotal() != null ? orden.getTotal() : BigDecimal.ZERO;
        BigDecimal pagado = pagos.stream().map(Pago::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean cobrado = orden.getEstado() == OrdenEstado.PAGADA;
        BigDecimal pendiente = cobrado ? BigDecimal.ZERO : total.subtract(pagado).max(BigDecimal.ZERO);
        List<com.nexora.restopos.dto.EntregaSeguimiento.PagoInfo> lista = pagos.stream()
                .map(p -> new com.nexora.restopos.dto.EntregaSeguimiento.PagoInfo(p.getMetodo().name(), p.getMonto(),
                        p.getUsuario() != null ? p.getUsuario().getName() : null, p.getCreatedAt()))
                .toList();
        com.nexora.restopos.dto.EntregaSeguimiento.Transferencia transferencia = null;
        if ("WEB".equals(orden.getOrigen()) && "TRANSFERENCIA".equals(orden.getMetodoPago())) {
            BigDecimal envio = OrdenService.costoEnvio(orden);
            // Sin envío: transfiere solo los platillos y el envío se le paga en efectivo al repartidor.
            boolean sinEnvio = Boolean.FALSE.equals(orden.getTransferenciaIncluyeEnvio());
            BigDecimal envioEfectivo = sinEnvio ? envio.min(total) : BigDecimal.ZERO;
            boolean confirmada = pagos.stream().anyMatch(p -> p.getMetodo() == PaymentMethod.TRANSFER);
            transferencia = new com.nexora.restopos.dto.EntregaSeguimiento.Transferencia(
                    orden.getComprobantePath() != null, orden.getComprobanteAt(), orden.getTransferenciaIncluyeEnvio(),
                    envio, total.subtract(envioEfectivo), envioEfectivo, confirmada);
        }
        return new com.nexora.restopos.dto.EntregaSeguimiento.Cobro(cobrado, total, pagado, pendiente,
                orden.getMetodoPago(), orden.getComprobantePath() != null, lista, transferencia);
    }

    /** Envío a domicilio de la tienda del actor, ya aceptado, no cancelado y sin entregar. */
    private Orden envioPendiente(Long ordenId, User actor) {
        Orden orden = ordenService.findById(ordenId, actor);
        if (!"DOMICILIO".equals(orden.getTipoEntrega())) throw new IllegalArgumentException("Este pedido no es a domicilio");
        if (orden.getEstado() == OrdenEstado.CANCELADA) throw new IllegalStateException("Este pedido fue cancelado");
        if (Boolean.TRUE.equals(orden.getPendienteAceptar())) throw new IllegalStateException("Este pedido todavía no se acepta");
        if (orden.getEntregadaAt() != null) throw new IllegalStateException("Este pedido ya se entregó");
        return orden;
    }
}
