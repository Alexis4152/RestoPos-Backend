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
        orden.setRepartidor(actor);
        Orden saved = ordenRepository.save(orden);
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
        auditoriaService.log(actor, "EN_CAMINO_ENTREGA", "Orden", saved.getId(), null, actor.getName(), null);
        return saved;
    }

    /** ¿El restaurante ya entregó todos los platillos (no cancelados) para llevar? */
    private static boolean entregadoAlRepartidor(Orden orden) {
        return orden.getItems().stream()
                .filter(i -> !Boolean.TRUE.equals(i.getCargoExtra()) && i.getEstado() != OrdenDetalleEstado.CANCELADO)
                .allMatch(i -> i.getEstado() == OrdenDetalleEstado.ENTREGADO);
    }

    /** Suelta un envío que había tomado (ej. se lo pasa a otro repartidor). Un admin puede soltar cualquiera. */
    @Transactional
    public Orden soltar(Long ordenId, User actor) {
        Orden orden = envioPendiente(ordenId, actor);
        if (orden.getRepartidor() == null) return orden;
        if (orden.getEnCaminoAt() != null) throw new IllegalStateException("Ya vas en camino con este pedido");
        if (!orden.getRepartidor().getId().equals(actor.getId()) && !tenantScope.isAdminLevel(actor)) {
            throw new IllegalStateException("Solo quien lleva el pedido puede soltarlo");
        }
        String anterior = orden.getRepartidor().getName();
        orden.setRepartidor(null);
        Orden saved = ordenRepository.save(orden);
        auditoriaService.log(actor, "SOLTAR_ENTREGA", "Orden", saved.getId(), anterior, null, null);
        return saved;
    }

    /**
     * Marca el envío como entregado. Si queda saldo, lo cobra primero (efectivo o
     * transferencia) en el corte de caja del repartidor — exige que lo tenga abierto, como
     * cualquier cobro. Al terminar, los platillos quedan ENTREGADO y la orden PAGADA.
     */
    @Transactional
    public Orden entregar(Long ordenId, EntregaRequest req, User actor) {
        Orden orden = envioPendiente(ordenId, actor);
        if (orden.getRepartidor() != null && !orden.getRepartidor().getId().equals(actor.getId())
                && !tenantScope.isAdminLevel(actor)) {
            throw new IllegalStateException("Este pedido lo lleva " + orden.getRepartidor().getName());
        }
        // Primero "Voy en camino" (el cliente de la web debe ver esa etapa antes de "Entregado";
        // mismo flujo para los envíos capturados en caja, para que el repartidor trabaje igual).
        if (orden.getEnCaminoAt() == null) {
            throw new IllegalStateException("Marca \"Voy en camino\" antes de entregar");
        }

        if (orden.getEstado() != OrdenEstado.PAGADA) {
            PaymentMethod metodo = req.getMetodo();
            if (metodo != PaymentMethod.CASH && metodo != PaymentMethod.TRANSFER) {
                throw new IllegalArgumentException("Elige cómo pagó el cliente: efectivo o transferencia");
            }
            cobrar(orden, metodo, req, actor);
        }

        // Releer: el cobro pasa por otros servicios que guardan la orden por su cuenta.
        orden = ordenService.findById(ordenId, actor);
        for (OrdenDetalle item : orden.getItems()) {
            if (item.getEstado() != OrdenDetalleEstado.CANCELADO && item.getEstado() != OrdenDetalleEstado.ENTREGADO) {
                ordenService.actualizarEstadoItem(orden.getId(), item.getId(), OrdenDetalleEstado.ENTREGADO, actor);
            }
        }
        orden = ordenService.findById(ordenId, actor);
        if (orden.getRepartidor() == null) orden.setRepartidor(actor);
        orden.setEntregadaAt(LocalDateTime.now());
        Orden saved = ordenRepository.save(orden);
        auditoriaService.log(actor, "ENTREGAR_PEDIDO", "Orden", saved.getId(), null,
                req.getMetodo() != null ? req.getMetodo().name() : "YA_PAGADO", null);
        return saved;
    }

    private void cobrar(Orden orden, PaymentMethod metodo, EntregaRequest req, User actor) {
        // Lo que todavía no está en ninguna cuenta se factura en una cuenta TOTAL.
        boolean faltaFacturar = orden.getItems().stream()
                .anyMatch(i -> i.getEstado() != OrdenDetalleEstado.CANCELADO
                        && i.getCantidadFacturada().compareTo(i.getCantidad()) < 0);
        if (faltaFacturar) {
            CuentaCreateRequest cr = new CuentaCreateRequest();
            cr.setTipo(CuentaTipo.TOTAL);
            cuentaService.crear(orden.getId(), cr, actor);
        }

        List<Cuenta> pendientes = cuentaService.findAllByOrden(orden.getId(), actor).stream()
                .filter(c -> c.getEstado() == CuentaEstado.PENDIENTE)
                .toList();
        BigDecimal saldo = pendientes.stream()
                .map(c -> c.getTotal().subtract(c.getTotalPagado()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // Igual que el cobro en caja: en efectivo hay que capturar con cuánto pagó (para el cambio).
        if (metodo == PaymentMethod.CASH && req.getRecibido() == null) {
            throw new IllegalArgumentException("Captura con cuánto pagó el cliente en efectivo");
        }
        if (metodo == PaymentMethod.CASH && req.getRecibido().compareTo(saldo) < 0) {
            throw new IllegalArgumentException("El efectivo recibido es menor al total a cobrar (" + saldo + ")");
        }

        boolean primero = true;
        for (Cuenta c : pendientes) {
            BigDecimal monto = c.getTotal().subtract(c.getTotalPagado());
            if (monto.compareTo(BigDecimal.ZERO) <= 0) continue;
            PagoRequest pago = new PagoRequest();
            pago.setMetodo(metodo);
            pago.setMonto(monto);
            // El efectivo recibido (para el cambio) se registra una sola vez, en el primer pago.
            if (metodo == PaymentMethod.CASH && primero) pago.setRecibido(req.getRecibido());
            if (metodo == PaymentMethod.TRANSFER) pago.setReferencia(req.getReferencia());
            pagoService.registrar(c.getId(), pago, actor);
            primero = false;
        }
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
