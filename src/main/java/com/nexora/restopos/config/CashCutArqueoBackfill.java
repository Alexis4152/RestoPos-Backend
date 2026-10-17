package com.nexora.restopos.config;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Marca el estado del arqueo ({@code cash_cuts.arqueo_status}) de los cortes que se
 * cerraron antes de que existiera: con diferencia → {@code DIFFERENCE} (para que un admin
 * la pueda resolver), cuadrados → {@code OK}. Solo toca cortes que siguen en {@code NONE},
 * así que es idempotente y nunca reabre una diferencia ya resuelta.
 */
@Component
@Order(31)
@RequiredArgsConstructor
@Slf4j
public class CashCutArqueoBackfill implements CommandLineRunner {

    private final EntityManager entityManager;

    @Override
    @Transactional
    public void run(String... args) {
        int withDiff = entityManager.createNativeQuery(
                "UPDATE cash_cuts SET arqueo_status = 'DIFFERENCE' " +
                "WHERE status = 'CLOSED' AND arqueo_status = 'NONE' AND counted_amount IS NOT NULL AND cash_difference <> 0").executeUpdate();
        int ok = entityManager.createNativeQuery(
                "UPDATE cash_cuts SET arqueo_status = 'OK' " +
                "WHERE status = 'CLOSED' AND arqueo_status = 'NONE' AND counted_amount IS NOT NULL AND cash_difference = 0").executeUpdate();
        if (withDiff + ok > 0) {
            log.info("Estado de arqueo marcado en cortes anteriores: {} con diferencia, {} cuadrados", withDiff, ok);
        }
    }
}
