package com.nexora.restopos.dto;

import java.math.BigDecimal;

/**
 * Resumen en vivo de una tienda para la pantalla "¿En qué tienda vas a trabajar?" de los
 * actores de plataforma (supervisor / super admin).
 */
public record TiendaResumen(Long tiendaId, int mesasOcupadas, int mesasTotal, int porAceptar,
                            BigDecimal ventasHoy, boolean corteAbierto) {}
