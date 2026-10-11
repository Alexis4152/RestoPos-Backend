package com.nexora.restopos.service;

import com.nexora.restopos.model.CashCut;
import com.nexora.restopos.model.Tienda;
import com.nexora.restopos.model.User;
import com.nexora.restopos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Llamado únicamente por el job programado (CashCutAutoCloseJob) una vez al día: agrupa
// TODOS los cortes cerrados del día por tienda y manda UN correo con Excel adjunto por
// cada tienda afectada, a su(s) admin(es). Se agrupa por el id de la tienda (no por el
// objeto Tienda) porque cada corte se cargó en su propia transacción y Tienda no tiene
// equals/hashCode propios — agrupar por objeto rompería el agrupado silenciosamente.
/**
 * Orquesta el envío del reporte diario de cierre de caja a los administradores de cada tienda.
 *
 * <p>Recibe la lista completa de cortes que se cerraron en el día (manuales y automáticos,
 * de todas las tiendas), los agrupa por tienda, genera un Excel por tienda con {@link
 * CashCutReportExcelService} y lo envía por correo (vía {@link EmailService}) a cada
 * administrador de esa tienda. Un fallo generando el Excel de una tienda no debe tumbar
 * el envío de las demás, por eso cada tienda se procesa de forma aislada.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CashCutReportNotifier {

    private final CashCutReportExcelService excelService;
    private final EmailService emailService;
    private final UserRepository userRepository;

    /**
     * Agrupa los cortes cerrados por tienda y dispara un correo con el reporte Excel
     * a cada administrador de cada tienda afectada.
     *
     * <p>Tiendas sin administradores registrados se omiten silenciosamente (no hay a
     * quién notificar). Si la generación del Excel de una tienda falla, se registra el
     * error y se continúa con las demás tiendas en vez de abortar todo el proceso.</p>
     *
     * @param closedCuts todos los cortes cerrados en el periodo notificado (típicamente,
     *                    el día), de cualquier tienda; si viene vacío no hace nada
     */
    public void notify(List<CashCut> closedCuts) {
        if (closedCuts.isEmpty()) return;

        Map<Long, List<CashCut>> byTiendaId = new LinkedHashMap<>();
        for (CashCut cut : closedCuts) {
            if (cut.getTienda() == null) continue;
            byTiendaId.computeIfAbsent(cut.getTienda().getId(), k -> new java.util.ArrayList<>()).add(cut);
        }

        for (Map.Entry<Long, List<CashCut>> entry : byTiendaId.entrySet()) {
            List<CashCut> cuts = entry.getValue();
            Tienda tienda = cuts.get(0).getTienda();
            List<User> admins = userRepository.findAdminsByTiendaId(tienda.getId());
            if (admins.isEmpty()) continue;

            byte[] excel;
            try {
                excel = excelService.build(tienda, cuts);
            } catch (RuntimeException e) {
                log.error("No se pudo generar el reporte de cierre para {}: {}", tienda.getName(), e.getMessage());
                continue;
            }

            for (User admin : admins) {
                emailService.sendCashCutReportEmail(tienda, cuts.size(), excel, admin.getEmail());
            }
        }
    }
}
