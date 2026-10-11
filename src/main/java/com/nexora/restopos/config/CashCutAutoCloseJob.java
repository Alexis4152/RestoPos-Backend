package com.nexora.restopos.config;

import com.nexora.restopos.model.CashCut;
import com.nexora.restopos.model.CashCutSchedule;
import com.nexora.restopos.model.CashCutStatus;
import com.nexora.restopos.repository.CashCutRepository;
import com.nexora.restopos.repository.CashCutScheduleRepository;
import com.nexora.restopos.service.CashCutReportNotifier;
import com.nexora.restopos.service.CashCutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Job programado que implementa el cierre automático de cortes de caja a la hora global
 * configurada en {@link CashCutSchedule}. Se ejecuta en todas las tiendas por igual: no hay
 * un horario por tienda, es una única hora para todo el sistema.
 *
 * <p>Diseño explícito del cliente: en lugar de mandar un correo por cada corte que se cierra,
 * este job cierra primero todos los cortes abiertos del día y después manda un único reporte
 * por tienda con el consolidado de todos los cortes cerrados ese día (tanto los cerrados a
 * mano por el cajero/vendedor como los que el propio job cerró). Los cierres manuales
 * ({@code CashCutService.close}) no disparan correo por su cuenta; solo este job notifica.</p>
 */
// Revisa cada minuto si ya es la hora configurada en cash_cut_schedule y, de ser así,
// cierra TODOS los cortes que sigan abiertos (de cualquier tienda) y manda UN solo reporte
// por tienda con TODOS los cortes del día — los que ya se habían cerrado a mano en cualquier
// momento del día y los que este job cierra justo ahora. La hora es global para todo el sistema.
// Los cierres manuales (CashCutService.close) NO mandan correo por su cuenta.
@Component
@RequiredArgsConstructor
@Slf4j
public class CashCutAutoCloseJob {

    private final CashCutScheduleRepository scheduleRepository;
    private final CashCutRepository cashCutRepository;
    private final CashCutService cashCutService;
    private final CashCutReportNotifier reportNotifier;

    // en memoria: si la app se reinicia justo en el minuto exacto, en el peor caso
    // corre dos veces ese día — aceptable, cerrar un corte ya cerrado simplemente no hace nada.
    private LocalDate lastRunDate;

    /**
     * Punto de entrada del job, invocado cada 60 segundos por el scheduler de Spring
     * ({@code fixedRate = 60_000}). Sale de inmediato si el horario está deshabilitado, si
     * la hora/minuto actuales no coinciden con {@link CashCutSchedule#getCloseHour()} /
     * {@link CashCutSchedule#getCloseMinute()}, o si ya se ejecutó hoy (guard en memoria
     * {@link #lastRunDate}). Cuando sí corresponde ejecutar, cierra todos los cortes abiertos
     * y envía el reporte diario consolidado por tienda.
     */
    @Scheduled(fixedRate = 60_000)
    public void checkAndRun() {
        CashCutSchedule schedule = scheduleRepository.findById(1L).orElse(null);
        if (schedule == null || !Boolean.TRUE.equals(schedule.getEnabled())) return;

        LocalDateTime now = LocalDateTime.now();
        if (now.getHour() != schedule.getCloseHour() || now.getMinute() != schedule.getCloseMinute()) return;
        if (now.toLocalDate().equals(lastRunDate)) return;
        lastRunDate = now.toLocalDate();

        closeAllOpenCuts();
        sendDailyReport();
    }

    /**
     * Cierra, uno por uno, todos los {@link CashCut} que sigan en estado {@code OPEN} sin
     * importar a qué tienda pertenezcan. Cada cierre se envuelve en su propio try/catch para
     * que si uno falla no impida cerrar el resto de los cortes pendientes.
     */
    private void closeAllOpenCuts() {
        List<CashCut> openCuts = cashCutRepository.findAllByStatus(CashCutStatus.OPEN);
        if (openCuts.isEmpty()) return;

        log.info("Cierre automático de cortes: {} corte(s) abierto(s) por cerrar", openCuts.size());
        for (CashCut cut : openCuts) {
            try {
                cashCutService.autoClose(cut.getId());
            } catch (Exception e) {
                log.error("No se pudo autocerrar el corte #{}: {}", cut.getId(), e.getMessage());
            }
        }
    }

    /**
     * Busca todos los cortes con estado {@code CLOSED} abiertos en el día en curso (manuales
     * o auto-cerrados) y delega a {@link CashCutReportNotifier} el envío del reporte
     * consolidado, agrupado por tienda.
     */
    private void sendDailyReport() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        List<CashCut> closedToday = cashCutRepository.findAllByStatusAndOpenedAtBetween(
                CashCutStatus.CLOSED, startOfDay, endOfDay);
        reportNotifier.notify(closedToday);
    }
}
