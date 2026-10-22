package com.nexora.restopos.dto;

import java.time.LocalDateTime;

/** Aviso de la campana: un repartidor soltó un envío (con su motivo). Ver OrdenService#avisosRecientes. */
public record AvisoSoltado(Long ordenId, String repartidor, String motivo, LocalDateTime fecha) {}
