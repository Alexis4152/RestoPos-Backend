package com.nexora.restopos.service;

import com.nexora.restopos.dto.PagoCorreccionRequest;
import com.nexora.restopos.dto.PagoDevolucionRequest;
import com.nexora.restopos.dto.PagoRequest;
import com.nexora.restopos.dto.TransferenciaPorVerificar;
import com.nexora.restopos.model.*;
import com.nexora.restopos.repository.CashCutRepository;
import com.nexora.restopos.repository.PagoRepository;
import com.nexora.restopos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Registra y cancela {@link Pago}s sobre una {@link Cuenta}. Un pago mixto se arma con varias
 * llamadas a {@link #registrar} sobre la misma cuenta, una por método de pago. Cada pago exige
 * que quien cobra tenga su propio {@link CashCut} abierto (igual que el punto de venta clásico),
 * para poder consolidarse en su corte. Después de cobrar: {@link #cancelar} si el dinero nunca
 * se quedó, {@link #corregirMetodo} si se capturó mal el método y {@link #devolver} si hay que
 * regresar dinero de una venta ya cerrada.
 */
@Service
@RequiredArgsConstructor
public class PagoService {

    private final PagoRepository pagoRepository;
    private final CashCutRepository cashCutRepository;
    private final CuentaService cuentaService;
    private final OrdenService ordenService;
    private final EmailService emailService;
    private final TenantScope tenantScope;
    private final AuditoriaService auditoriaService;
    private final CashBalanceCalculator cashBalanceCalculator;
    private final jakarta.persistence.EntityManager entityManager;

    public static final String POR_VERIFICAR = "POR_VERIFICAR";
    public static final String VERIFICADA = "VERIFICADA";
    public static final String NO_RECIBIDA = "NO_RECIBIDA";

    @Transactional
    public Pago registrar(Long cuentaId, PagoRequest req, User actor) {
        // Bloqueada hasta el final: dos cobros simultáneos a esta cuenta van uno tras otro.
        Cuenta cuenta = cuentaService.bloquear(cuentaId, actor);
        String llave = req.getIdempotencyKey() != null && !req.getIdempotencyKey().isBlank() ? req.getIdempotencyKey().trim() : null;
        if (llave != null) {
            java.util.Optional<Pago> previo = pagoRepository.findByCuentaIdAndIdempotencyKey(cuentaId, llave);
            if (previo.isPresent()) return previo.get();
        }
        if (cuenta.getEstado() != CuentaEstado.PENDIENTE) {
            throw new IllegalStateException("La cuenta ya no está pendiente de pago");
        }
        if (cuenta.getOrden().getEstado() == OrdenEstado.CANCELADA) {
            throw new IllegalStateException("Esta orden fue cancelada");
        }
        String referencia = req.getReferencia() != null && !req.getReferencia().isBlank() ? req.getReferencia().trim() : null;
        if (req.getMetodo() == PaymentMethod.TRANSFER && referencia == null) {
            throw new IllegalArgumentException("Captura la referencia o folio de la transferencia");
        }
        if (req.getMetodo() == PaymentMethod.CASH && req.getRecibido() != null && req.getRecibido().compareTo(req.getMonto()) < 0) {
            throw new IllegalArgumentException("El efectivo recibido no cubre el monto del pago");
        }
        CashCut openCut = cashCutRepository.findFirstByUserIdAndStatus(actor.getId(), CashCutStatus.OPEN)
                .orElseThrow(() -> new IllegalStateException("Debes abrir un corte de caja antes de registrar pagos"));

        // Propina opcional: solo tiene efecto junto con el PRIMER pago de la cuenta (antes de
        // eso su total sigue siendo puro items, ver CuentaService#crearInternal) — una vez que
        // ya hay algo pagado, se ignora cualquier propina que llegue para no alterar un total
        // contra el que ya se registraron pagos.
        if (cuenta.getTotalPagado().compareTo(BigDecimal.ZERO) == 0
                && (req.getPropinaPorcentaje() != null || req.getPropinaMonto() != null)) {
            BigDecimal subtotal = cuenta.getTotal().subtract(cuenta.getPropina());
            // Tope configurable por tienda ("Datos de la tienda"), 50% si no está definido.
            Tienda tienda = cuenta.getOrden() != null ? cuenta.getOrden().getTienda() : null;
            Integer tope = tienda != null ? tienda.getPropinaMaxPorcentaje() : null;
            int topePct = tope != null ? tope : 50;
            BigDecimal propina;
            if (req.getPropinaPorcentaje() != null) {
                if (req.getPropinaPorcentaje().compareTo(BigDecimal.valueOf(topePct)) > 0) {
                    throw new IllegalArgumentException("La propina no puede pasar del " + topePct + "%");
                }
                propina = subtotal.multiply(req.getPropinaPorcentaje()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                cuenta.setPropinaPorcentaje(req.getPropinaPorcentaje());
            } else {
                propina = req.getPropinaMonto().setScale(2, RoundingMode.HALF_UP);
                cuenta.setPropinaPorcentaje(null);
                BigDecimal maximo = subtotal.multiply(BigDecimal.valueOf(topePct)).divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);
                if (propina.compareTo(maximo) > 0) {
                    throw new IllegalArgumentException(String.format("La propina no puede pasar del %d%% de la cuenta ($%,.2f)", topePct, maximo));
                }
            }
            cuenta.setPropina(propina);
            cuenta.setTotal(subtotal.add(propina));
        }

        BigDecimal saldo = cuenta.getTotal().subtract(cuenta.getTotalPagado());
        if (req.getMonto().compareTo(saldo) > 0) {
            throw new IllegalStateException("El monto excede el saldo pendiente de la cuenta (" + saldo + ")");
        }

        Pago pago = new Pago();
        pago.setCuenta(cuenta);
        pago.setMetodo(req.getMetodo());
        pago.setMonto(req.getMonto());
        pago.setRecibido(req.getRecibido());
        pago.setReferencia(referencia);
        pago.setIdempotencyKey(llave);
        if (req.getMetodo() == PaymentMethod.TRANSFER) pago.setVerificacion(POR_VERIFICAR);
        pago.setCashCut(openCut);
        pago.setUsuario(actor);
        Pago saved = pagoRepository.save(pago);

        cuenta.getPagos().add(saved);
        cuenta.setTotalPagado(cuenta.getTotalPagado().add(saved.getMonto()));
        if (cuenta.getTotalPagado().compareTo(cuenta.getTotal()) >= 0) {
            cuenta.setEstado(CuentaEstado.PAGADA);
            cuenta.setClosedAt(LocalDateTime.now());
        }
        if (req.getEmailTicket() != null && !req.getEmailTicket().isBlank()) {
            cuenta.setTicketEmailDestino(req.getEmailTicket());
        }
        cuenta = cuentaService.save(cuenta);

        if (cuenta.getEstado() == CuentaEstado.PAGADA) {
            ordenService.marcarPagadaSiCorresponde(cuenta.getOrden());
        }
        if (req.getEmailTicket() != null && !req.getEmailTicket().isBlank()) {
            emailService.sendTicketEmail(cuenta, req.getEmailTicket());
        }
        return saved;
    }

    /**
     * Cancela un pago: el dinero nunca se quedó (cobro equivocado). Solo con su corte abierto y
     * la orden sin cerrar; si ya no se puede, lo que aplica es {@link #devolver}.
     */
    @Transactional
    public void cancelar(Long pagoId, String motivo, User actor) {
        Pago pago = pagoBloqueado(pagoId, actor);
        String m = motivoObligatorio(motivo);
        if (Boolean.TRUE.equals(pago.getCancelado())) {
            throw new IllegalStateException("El pago ya está cancelado");
        }
        exigirPagoNormal(pago);
        Cuenta cuenta = pago.getCuenta();
        if (cuenta.getOrden().getEstado() == OrdenEstado.PAGADA) {
            throw new IllegalStateException("No se puede cancelar: la orden ya quedó cerrada. Si hay que regresar dinero, usa Devolver");
        }
        if (pago.getCashCut().getStatus() != CashCutStatus.OPEN) {
            throw new IllegalStateException("No se puede cancelar: ese pago es de un corte ya cerrado. Si hay que regresar dinero, usa Devolver");
        }

        pago.setCancelado(true);
        pago.setCancelledBy(actor);
        pago.setCancelledAt(LocalDateTime.now());
        pago.setMotivoCancelacion(m);
        pagoRepository.save(pago);

        cuenta.setTotalPagado(cuenta.getTotalPagado().subtract(pago.getMonto()));
        if (cuenta.getEstado() == CuentaEstado.PAGADA) {
            cuenta.setEstado(CuentaEstado.PENDIENTE);
            cuenta.setClosedAt(null);
        }
        // Sin ningún pago, la propina que venía con ellos ya no aplica: se decide al volver a cobrar.
        if (cuenta.getTotalPagado().signum() == 0 && cuenta.getPropina() != null && cuenta.getPropina().signum() > 0) {
            cuenta.setTotal(cuenta.getTotal().subtract(cuenta.getPropina()));
            cuenta.setPropina(BigDecimal.ZERO);
            cuenta.setPropinaPorcentaje(null);
        }
        cuentaService.save(cuenta);
        ordenService.reabrirCobro(cuenta.getOrden());
        auditoriaService.log(actor, "CANCELAR_PAGO", "Pago", pago.getId(), false, true, m);
    }

    /**
     * Corrige el método de un pago mal capturado (efectivo ↔ tarjeta ↔ transferencia) mientras
     * su corte siga abierto: el corte se calcula de los pagos, así que queda cuadrado solo.
     */
    @Transactional
    public Pago corregirMetodo(Long pagoId, PagoCorreccionRequest req, User actor) {
        Pago pago = pagoBloqueado(pagoId, actor);
        String m = motivoObligatorio(req.getMotivo());
        exigirPagoVigente(pago);
        if (pago.getMetodo() == req.getMetodo()) throw new IllegalArgumentException("El pago ya está registrado con ese método");
        if (pago.getCashCut().getStatus() != CashCutStatus.OPEN) {
            throw new IllegalStateException("No se puede corregir: ese pago es de un corte ya cerrado");
        }
        if (pagoRepository.devueltoDe(pagoId).signum() > 0) {
            throw new IllegalStateException("No se puede corregir: ese pago ya tiene una devolución");
        }
        String referencia = req.getReferencia() != null && !req.getReferencia().isBlank() ? req.getReferencia().trim() : null;
        if (req.getMetodo() == PaymentMethod.TRANSFER && referencia == null) {
            throw new IllegalArgumentException("Captura la referencia o folio de la transferencia");
        }
        PaymentMethod anterior = pago.getMetodo();
        pago.setMetodo(req.getMetodo());
        pago.setReferencia(referencia);
        pago.setRecibido(req.getMetodo() == PaymentMethod.CASH ? pago.getMonto() : null);
        pago.setVerificacion(req.getMetodo() == PaymentMethod.TRANSFER ? POR_VERIFICAR : null);
        pago.setVerificadaPor(null);
        pago.setVerificadaAt(null);
        Pago saved = pagoRepository.save(pago);
        auditoriaService.log(actor, "CORREGIR_METODO_PAGO", "Pago", pago.getId(), anterior, req.getMetodo(), m);
        return saved;
    }

    /**
     * Regresa dinero al cliente de un pago que ya no se puede cancelar (orden cerrada o corte
     * cerrado). Se registra como un pago negativo en el corte abierto de quien devuelve, con el
     * mismo método del pago original, así que sale de su caja y de sus ingresos del día.
     */
    @Transactional
    public Pago devolver(Long pagoId, PagoDevolucionRequest req, User actor) {
        Pago pago = pagoBloqueado(pagoId, actor);
        String m = motivoObligatorio(req.getMotivo());
        exigirPagoVigente(pago);
        Cuenta cuenta = pago.getCuenta();
        if (cuenta.getOrden().getEstado() != OrdenEstado.PAGADA && pago.getCashCut().getStatus() == CashCutStatus.OPEN) {
            throw new IllegalStateException("Este pago todavía se puede cancelar: usa Cancelar pago");
        }
        BigDecimal disponible = pago.getMonto().subtract(pagoRepository.devueltoDe(pagoId));
        if (req.getMonto().compareTo(disponible) > 0) {
            throw new IllegalArgumentException(String.format("Solo se pueden devolver $%,.2f de este pago", disponible));
        }
        CashCut openCut = cashCutRepository.findFirstByUserIdAndStatus(actor.getId(), CashCutStatus.OPEN)
                .orElseThrow(() -> new IllegalStateException("Debes abrir un corte de caja antes de devolver dinero"));
        if (pago.getMetodo() == PaymentMethod.CASH && req.getMonto().compareTo(cashBalanceCalculator.expectedCash(openCut)) > 0) {
            throw new IllegalStateException("No hay suficiente efectivo en tu caja para esta devolución");
        }

        Pago devolucion = new Pago();
        devolucion.setCuenta(cuenta);
        devolucion.setMetodo(pago.getMetodo());
        devolucion.setMonto(req.getMonto().negate());
        devolucion.setReferencia(recortar("Devolución: " + m, 100));
        devolucion.setPagoOrigenId(pago.getId());
        devolucion.setCashCut(openCut);
        devolucion.setUsuario(actor);
        Pago saved = pagoRepository.save(devolucion);
        cuenta.getPagos().add(saved);

        // Cuenta sin liquidar: lo devuelto vuelve a deberse. Cuenta pagada: la venta ocurrió y se
        // lleva aparte cuánto de ella se regresó.
        if (cuenta.getEstado() == CuentaEstado.PENDIENTE) {
            cuenta.setTotalPagado(cuenta.getTotalPagado().subtract(req.getMonto()));
        } else {
            cuenta.setTotalDevuelto(cuenta.getTotalDevuelto().add(req.getMonto()));
        }
        cuentaService.save(cuenta);
        auditoriaService.log(actor, "DEVOLVER_PAGO", "Pago", pago.getId(), pago.getMonto(), req.getMonto(), m);
        return saved;
    }

    /** Confirma si una transferencia sí llegó al banco, o la marca como no recibida. */
    @Transactional
    public Pago verificar(Long pagoId, String resultado, User actor) {
        Pago pago = pagoBloqueado(pagoId, actor);
        exigirPagoVigente(pago);
        if (pago.getMetodo() != PaymentMethod.TRANSFER || pago.getVerificacion() == null) {
            throw new IllegalStateException("Este pago no es una transferencia por verificar");
        }
        if (!VERIFICADA.equals(resultado) && !NO_RECIBIDA.equals(resultado)) {
            throw new IllegalArgumentException("Resultado no válido");
        }
        if (VERIFICADA.equals(pago.getVerificacion())) throw new IllegalStateException("Esta transferencia ya está verificada");
        String anterior = pago.getVerificacion();
        pago.setVerificacion(resultado);
        pago.setVerificadaPor(actor.getName());
        pago.setVerificadaAt(LocalDateTime.now());
        Pago saved = pagoRepository.save(pago);
        auditoriaService.log(actor, "VERIFICAR_TRANSFERENCIA", "Pago", pago.getId(), anterior, resultado, null);
        return saved;
    }

    /** Transferencias pendientes de confirmar en el banco (o marcadas como no recibidas). */
    @Transactional(readOnly = true)
    public List<TransferenciaPorVerificar> porVerificar(User actor) {
        return pagoRepository.transferenciasPorVerificar(tenantScope.scopeId(actor)).stream().map(p -> {
            Orden o = p.getCuenta().getOrden();
            return new TransferenciaPorVerificar(p.getId(), o.getId(), p.getCuenta().getTicketFolio(), p.getMonto(),
                    p.getReferencia(), p.getVerificacion(), o.getClienteNombre(),
                    p.getUsuario() != null ? p.getUsuario().getName() : null, o.getComprobantePath() != null, p.getCreatedAt());
        }).toList();
    }

    /** Pago de la tienda del actor, con su cuenta bloqueada para que nada la cambie a la vez. */
    private Pago pagoBloqueado(Long pagoId, User actor) {
        Pago pago = pagoRepository.findById(pagoId)
                .orElseThrow(() -> new IllegalArgumentException("Pago no encontrado: " + pagoId));
        Long scope = tenantScope.scopeId(actor);
        Tienda tienda = pago.getCuenta().getOrden().getTienda();
        if (scope != null && (tienda == null || !scope.equals(tienda.getId()))) {
            throw new IllegalArgumentException("Pago no encontrado: " + pagoId);
        }
        cuentaService.bloquear(pago.getCuenta().getId(), actor);
        entityManager.refresh(pago);
        return pago;
    }

    private void exigirPagoNormal(Pago pago) {
        if (pago.getPagoOrigenId() != null) throw new IllegalStateException("Una devolución no se puede modificar");
    }

    private void exigirPagoVigente(Pago pago) {
        if (Boolean.TRUE.equals(pago.getCancelado())) throw new IllegalStateException("El pago está cancelado");
        exigirPagoNormal(pago);
    }

    private static String motivoObligatorio(String motivo) {
        String m = motivo == null ? "" : motivo.trim();
        if (m.length() < 3) throw new IllegalArgumentException("Escribe el motivo");
        return recortar(m, 255);
    }

    private static String recortar(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
