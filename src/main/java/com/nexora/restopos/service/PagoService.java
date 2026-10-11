package com.nexora.restopos.service;

import com.nexora.restopos.dto.PagoRequest;
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

/**
 * Registra y cancela {@link Pago}s sobre una {@link Cuenta}. Un pago mixto se arma con varias
 * llamadas a {@link #registrar} sobre la misma cuenta, una por método de pago. Cada pago exige
 * que quien cobra tenga su propio {@link CashCut} abierto (igual que el punto de venta clásico),
 * para poder consolidarse en su corte.
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

    @Transactional
    public Pago registrar(Long cuentaId, PagoRequest req, User actor) {
        Cuenta cuenta = cuentaService.findById(cuentaId, actor);
        if (cuenta.getEstado() != CuentaEstado.PENDIENTE) {
            throw new IllegalStateException("La cuenta ya no está pendiente de pago");
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
            BigDecimal propina;
            if (req.getPropinaPorcentaje() != null) {
                propina = subtotal.multiply(req.getPropinaPorcentaje()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                cuenta.setPropinaPorcentaje(req.getPropinaPorcentaje());
            } else {
                propina = req.getPropinaMonto().setScale(2, RoundingMode.HALF_UP);
                cuenta.setPropinaPorcentaje(null);
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
        pago.setReferencia(req.getReferencia());
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

    @Transactional
    public void cancelar(Long pagoId, String motivo, User actor) {
        Pago pago = pagoRepository.findById(pagoId)
                .orElseThrow(() -> new IllegalArgumentException("Pago no encontrado: " + pagoId));
        Long scope = tenantScope.scopeId(actor);
        Tienda tienda = pago.getCuenta().getOrden().getTienda();
        if (scope != null && (tienda == null || !scope.equals(tienda.getId()))) {
            throw new IllegalArgumentException("Pago no encontrado: " + pagoId);
        }
        if (Boolean.TRUE.equals(pago.getCancelado())) {
            throw new IllegalStateException("El pago ya está cancelado");
        }
        Cuenta cuenta = pago.getCuenta();
        if (cuenta.getOrden().getEstado() == OrdenEstado.PAGADA) {
            throw new IllegalStateException("No se puede cancelar: la orden ya quedó cerrada y la mesa liberada");
        }

        pago.setCancelado(true);
        pago.setCancelledBy(actor);
        pago.setCancelledAt(LocalDateTime.now());
        pago.setMotivoCancelacion(motivo);
        pagoRepository.save(pago);

        cuenta.setTotalPagado(cuenta.getTotalPagado().subtract(pago.getMonto()));
        if (cuenta.getEstado() == CuentaEstado.PAGADA) {
            cuenta.setEstado(CuentaEstado.PENDIENTE);
            cuenta.setClosedAt(null);
        }
        cuentaService.save(cuenta);
        auditoriaService.log(actor, "CANCELAR_PAGO", "Pago", pago.getId(), false, true, motivo);
    }
}
