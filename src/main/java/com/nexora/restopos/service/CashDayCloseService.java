package com.nexora.restopos.service;

import com.nexora.restopos.model.CashCut;
import com.nexora.restopos.model.CashCutStatus;
import com.nexora.restopos.model.CashRegister;
import com.nexora.restopos.repository.CashCutRepository;
import com.nexora.restopos.repository.CashRegisterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Fin de día de las cajas. Cada caja reporta, en un solo correo con un solo Excel, sus
 * cortes desde su último fin de día (los traspasos del día más el corte final) y queda
 * bloqueada hasta el siguiente día.
 * <ul>
 *   <li>{@link #finishDay}: lo dispara el botón "Corte de fin de día" de una caja.</li>
 *   <li>{@link #runScheduledDayEnd}: lo dispara el job programado — reporta las cajas que
 *   tengan cortes sin reportar y vuelve a abrir todas las cajas.</li>
 * </ul>
 * Si el job está apagado o falla, una caja bloqueada se reabre sola al cambiar de día (ver
 * {@link #isDayClosed}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CashDayCloseService {

    private final CashRegisterRepository registerRepository;
    private final CashCutRepository cashCutRepository;
    private final CashCutReportNotifier reportNotifier;

    /** {@code true} si la caja hizo su fin de día y todavía no se reabre (ni por el job ni por cambio de día). */
    public static boolean isDayClosed(CashRegister r) {
        return Boolean.TRUE.equals(r.getDayClosed()) && r.getLastDayCloseAt() != null
                && !r.getLastDayCloseAt().toLocalDate().isBefore(LocalDate.now());
    }

    /**
     * Fin de día de una caja: manda el reporte de sus cortes desde su último fin de día (o
     * desde el inicio del día, la primera vez) y la bloquea. Corre en la misma transacción
     * que el cierre del corte final, así que ese corte ya va incluido.
     *
     * @return cuántos cortes se reportaron
     */
    @Transactional
    public int finishDay(Long registerId) {
        CashRegister r = registerRepository.findByIdForUpdate(registerId).orElseThrow();
        List<CashCut> cuts = unreportedCuts(r);
        r.setLastDayCloseAt(LocalDateTime.now());
        r.setDayClosed(true);
        registerRepository.save(r);
        if (!cuts.isEmpty()) reportNotifier.notifyRegisterDay(r, cuts);
        return cuts.size();
    }

    /**
     * Lo que hace el job al llegar la hora programada (después de cerrar los cortes que
     * seguían abiertos): reporta cada caja que tenga cortes sin reportar, reporta los cortes
     * del día que no tienen caja (anteriores a las cajas) y vuelve a abrir todas las cajas.
     */
    @Transactional
    public void runScheduledDayEnd() {
        for (CashRegister r : registerRepository.findAll()) {
            try {
                List<CashCut> cuts = unreportedCuts(r);
                if (!cuts.isEmpty()) {
                    reportNotifier.notifyRegisterDay(r, cuts);
                    r.setLastDayCloseAt(LocalDateTime.now());
                }
                r.setDayClosed(false);
                registerRepository.save(r);
            } catch (RuntimeException e) {
                log.error("Fin de día programado: no se pudo procesar la caja #{}: {}", r.getId(), e.getMessage());
            }
        }
        LocalDateTime start = LocalDate.now().atStartOfDay();
        List<CashCut> legacy = cashCutRepository.findByCashRegisterIsNullAndStatusAndClosedAtBetween(
                CashCutStatus.CLOSED, start, start.plusDays(1));
        reportNotifier.notify(legacy);
    }

    private List<CashCut> unreportedCuts(CashRegister r) {
        LocalDateTime from = r.getLastDayCloseAt() != null ? r.getLastDayCloseAt() : LocalDate.now().atStartOfDay();
        return cashCutRepository.findClosedByRegisterSince(r.getId(), from);
    }
}
