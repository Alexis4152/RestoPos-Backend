package com.nexora.restopos.repository;

import com.nexora.restopos.model.MailConfig;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio de la fila única de {@link MailConfig} (configuración SMTP global). */
public interface MailConfigRepository extends JpaRepository<MailConfig, Long> {
}
