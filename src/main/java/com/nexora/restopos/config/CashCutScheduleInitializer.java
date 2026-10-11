package com.nexora.restopos.config;

import com.nexora.restopos.model.CashCutSchedule;
import com.nexora.restopos.repository.CashCutScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * {@link CommandLineRunner} que siembra al arrancar la aplicación la fila de configuración
 * (id=1) del horario global de cierre automático de cortes de caja, usada por
 * {@link CashCutAutoCloseJob}. Es idempotente: si la fila ya existe no hace nada.
 */
// Siembra la fila única (id=1) del horario de cierre automático de cortes de caja si no
// existe todavía. Queda deshabilitada (enabled=false) por default a propósito: el
// SUPER_ADMIN debe entrar y elegir la hora + activarla, no queremos que empiece a cerrar
// cortes reales solo porque alguien nunca la configuró.
@Component
@RequiredArgsConstructor
@Slf4j
public class CashCutScheduleInitializer implements CommandLineRunner {

    private final CashCutScheduleRepository scheduleRepository;

    /**
     * Crea el registro por defecto del horario (23:00, deshabilitado) si todavía no existe
     * ninguno en {@code cash_cut_schedule}. No sobrescribe una configuración ya existente.
     */
    @Override
    public void run(String... args) {
        if (scheduleRepository.existsById(1L)) return;
        CashCutSchedule schedule = CashCutSchedule.builder()
                .id(1L)
                .closeHour(23)
                .closeMinute(0)
                .enabled(false)
                .build();
        scheduleRepository.save(schedule);
        log.info("Horario de cierre automático de cortes sembrado (23:00, deshabilitado)");
    }
}
