package com.nexora.restopos.config;

import com.nexora.restopos.service.OrdenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Cada hora: las órdenes para llevar, comer aquí y recoger que se cobraron hace más de un día
 * y nadie marcó como entregadas se dan por entregadas y se cierran (ver
 * OrdenService#cerrarCobradasSinEntregar). A domicilio no se toca.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CobradasSinEntregarJob {

    private final OrdenService ordenService;

    @Scheduled(cron = "0 40 * * * *")
    public void cerrar() {
        try {
            int n = ordenService.cerrarCobradasSinEntregar(LocalDateTime.now().minusDays(1));
            if (n > 0) log.info("Se cerraron {} órdenes cobradas hace más de un día sin marcar la entrega", n);
        } catch (RuntimeException e) {
            log.error("No se pudieron cerrar las órdenes cobradas sin entregar: {}", e.getMessage(), e);
        }
    }
}
