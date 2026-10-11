package com.nexora.restopos.config;

import com.nexora.restopos.repository.ClienteSesionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Borra cada hora las sesiones y los enlaces de correo de clientes (página web) ya vencidos. */
@Component
@RequiredArgsConstructor
public class ClienteSesionPurgeJob {

    private final ClienteSesionRepository sesionRepository;
    private final com.nexora.restopos.repository.ClienteTokenRepository tokenRepository;

    @Scheduled(cron = "0 15 * * * *")
    @Transactional
    public void purgar() {
        sesionRepository.deleteExpired(LocalDateTime.now());
        // Enlaces de correo (verificar / restablecer) vencidos hace más de un día.
        tokenRepository.deleteVencidos(LocalDateTime.now().minusDays(1));
    }
}
