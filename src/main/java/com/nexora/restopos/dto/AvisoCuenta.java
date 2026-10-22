package com.nexora.restopos.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Aviso de la campana para la caja: se generó una cuenta que falta cobrar (el cliente viene a
 * pagar con su ticket). Ver OrdenService#avisosRecientes.
 */
public record AvisoCuenta(Long cuentaId, Long ordenId, String folio, String lugar, BigDecimal total,
                          String generadaPor, LocalDateTime fecha) {}
