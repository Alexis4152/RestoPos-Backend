package com.nexora.restopos.service;

import com.nexora.restopos.dto.MovimientoCajaRequest;
import com.nexora.restopos.model.CashCut;
import com.nexora.restopos.model.CashCutStatus;
import com.nexora.restopos.model.CashMovementType;
import com.nexora.restopos.model.MovimientoCaja;
import com.nexora.restopos.model.User;
import com.nexora.restopos.repository.CashCutRepository;
import com.nexora.restopos.repository.MovimientoCajaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/** Entradas, salidas y ajustes manuales de efectivo sobre el corte de caja abierto del actor. */
@Service
@RequiredArgsConstructor
public class MovimientoCajaService {

    private final MovimientoCajaRepository movimientoCajaRepository;
    private final CashCutRepository cashCutRepository;
    private final AuditoriaService auditoriaService;

    public MovimientoCaja registrar(MovimientoCajaRequest req, User actor) {
        CashCut openCut = cashCutRepository.findFirstByUserIdAndStatus(actor.getId(), CashCutStatus.OPEN)
                .orElseThrow(() -> new IllegalStateException("Debes abrir un corte de caja antes de registrar movimientos"));
        validarSigno(req.getTipo(), req.getMonto());

        MovimientoCaja mv = new MovimientoCaja();
        mv.setCashCut(openCut);
        mv.setTipo(req.getTipo());
        mv.setMonto(req.getMonto());
        mv.setMotivo(req.getMotivo());
        mv.setUsuario(actor);
        MovimientoCaja saved = movimientoCajaRepository.save(mv);
        auditoriaService.log(actor, "MOVIMIENTO_CAJA_" + req.getTipo(), "MovimientoCaja", saved.getId(), null, saved, req.getMotivo());
        return saved;
    }

    public List<MovimientoCaja> listByCashCut(Long cashCutId) {
        return movimientoCajaRepository.findByCashCutIdOrderByCreatedAtAsc(cashCutId);
    }

    // ENTRADA/SALIDA siempre se guardan con magnitud positiva (el signo lo aplica el cálculo
    // del cierre según el tipo, ver CashCutService.sumarMovimientos); un AJUSTE sí puede ser
    // negativo (corrige la caja hacia abajo), pero nunca cero — sin efecto no tiene sentido
    // registrarlo.
    private void validarSigno(CashMovementType tipo, BigDecimal monto) {
        if (tipo == CashMovementType.AJUSTE) {
            if (monto.compareTo(BigDecimal.ZERO) == 0) {
                throw new IllegalArgumentException("El ajuste no puede ser de cero");
            }
        } else if (monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a cero");
        }
    }
}
