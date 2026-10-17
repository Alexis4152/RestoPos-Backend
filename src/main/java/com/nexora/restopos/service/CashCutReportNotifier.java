package com.nexora.restopos.service;

import com.nexora.restopos.model.CashCut;
import com.nexora.restopos.model.CashRegister;
import com.nexora.restopos.model.Tienda;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Arma el Excel de cierre (vía {@link CashCutReportExcelService}) y lo envía por correo
 * ({@link EmailService}) a los destinatarios de la tienda ({@link CashReportRecipientService}:
 * el supervisor por default, más los usuarios y correos que haya configurado).
 * <ul>
 *   <li>{@link #notifyRegisterDay}: reporte de fin de día de UNA caja (un correo, un Excel con
 *   sus cortes del día) — botón "Corte de fin de día" o el job programado.</li>
 *   <li>{@link #notify}: cortes sin caja (anteriores a las cajas), agrupados por tienda — solo el job.</li>
 * </ul>
 * Debe llamarse dentro de una transacción: lee relaciones LAZY (supervisor, ventas). El envío
 * en sí es asíncrono y "best effort" (ver EmailService).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CashCutReportNotifier {

    private final CashCutReportExcelService excelService;
    private final EmailService emailService;
    private final CashReportRecipientService recipientService;

    /** Reporte de fin de día de una caja: un solo correo con un solo Excel por destinatario. */
    public void notifyRegisterDay(CashRegister register, List<CashCut> cuts) {
        if (cuts.isEmpty()) return;
        // La tienda del corte (EAGER, entidad real) y no la de la caja (proxy LAZY): el envío
        // es asíncrono y corre ya fuera de esta transacción.
        Tienda tienda = cuts.get(0).getTienda();
        byte[] excel;
        try {
            excel = excelService.build(tienda, cuts);
        } catch (RuntimeException e) {
            log.error("No se pudo generar el reporte de fin de día de {} / {}: {}", tienda.getName(), register.getName(), e.getMessage());
            return;
        }
        Set<String> to = recipientService.resolveEmails(tienda);
        for (String email : to) {
            emailService.sendCashRegisterDayReportEmail(tienda, register.getName(), cuts.size(), excel, email);
        }
        log.info("Reporte de fin de día de {} / {} ({} corte(s)) enviado a {} destinatario(s)", tienda.getName(), register.getName(), cuts.size(), to.size());
    }

    /** Cortes sin caja (anteriores a las cajas), un correo por tienda. */
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
            byte[] excel;
            try {
                excel = excelService.build(tienda, cuts);
            } catch (RuntimeException e) {
                log.error("No se pudo generar el reporte de cierre para {}: {}", tienda.getName(), e.getMessage());
                continue;
            }
            for (String email : recipientService.resolveEmails(tienda)) {
                emailService.sendCashCutReportEmail(tienda, cuts.size(), excel, email);
            }
        }
    }
}
