package com.nexora.restopos.service;

import com.nexora.restopos.dto.CashCutScheduleRequest;
import com.nexora.restopos.model.CashCutSchedule;
import com.nexora.restopos.model.User;
import com.nexora.restopos.repository.CashCutScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Administra la configuración global del cierre automático de cortes de caja.
 *
 * <p>Guarda la hora del día a la que corre {@code CashCutAutoCloseJob} (paquete
 * {@code config}) para cerrar automáticamente los cortes que sigan abiertos, y si ese
 * cierre automático está habilitado o no. Es una configuración de una sola fila
 * (id fijo = 1), no por tienda: aplica igual para todas.</p>
 */
@Service
@RequiredArgsConstructor
public class CashCutScheduleService {

    private final CashCutScheduleRepository scheduleRepository;

    /**
     * Obtiene la configuración vigente del horario de cierre automático.
     *
     * @return la configuración única (id = 1) de horario de cierre
     * @throws IllegalStateException si aún no existe la fila de configuración (debería
     *         sembrarse al iniciar la aplicación)
     */
    public CashCutSchedule get() {
        return scheduleRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("No se ha configurado el horario de cierre"));
    }

    /**
     * Actualiza la hora, minuto y estado habilitado/deshabilitado del cierre automático.
     *
     * @param req nueva hora, minuto y bandera de habilitado
     * @param actor usuario que hace el cambio, registrado como {@code updatedBy}
     * @return la configuración ya actualizada
     */
    public CashCutSchedule update(CashCutScheduleRequest req, User actor) {
        CashCutSchedule schedule = get();
        schedule.setCloseHour(req.getCloseHour());
        schedule.setCloseMinute(req.getCloseMinute());
        schedule.setEnabled(req.getEnabled());
        schedule.setUpdatedBy(actor);
        return scheduleRepository.save(schedule);
    }
}
