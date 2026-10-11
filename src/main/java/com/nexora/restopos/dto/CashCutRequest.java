package com.nexora.restopos.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Payload usado tanto para abrir como para cerrar un corte de caja
 * ({@code POST /api/cash-cuts/open} y {@code POST /api/cash-cuts/{id}/close}).
 * Cada cajero/vendedor puede abrir su propio {@code CashCut} diario con un fondo inicial;
 * varios cortes pueden estar abiertos al mismo tiempo en una misma tienda, uno por usuario.
 * <p>
 * El significado de los campos cambia según la operación: al abrir, {@code amount} es el
 * fondo inicial en caja y {@code expenses} no se usa; al cerrar, {@code amount} se ignora y
 * lo relevante son {@code expenses} (gastos capturados durante el turno) y {@code notes}.
 */
@Data
public class CashCutRequest {
    // Fondo inicial en caja al abrir el corte. Requerido y validado solo en la apertura;
    // en el cierre este valor no se utiliza. Tope = NUMERIC(12,2) de CashCut.openingAmount;
    // sin este @DecimalMax, un monto fuera de rango revienta en un error crudo de base de
    // datos en vez de un 400 legible.
    @NotNull @PositiveOrZero
    @DecimalMax(value = "9999999999.99", message = "El número es excesivamente grande — el máximo permitido es 9,999,999,999.99")
    private BigDecimal amount;
    // Gastos del turno, capturados únicamente al cerrar el corte manualmente
    // (el cierre automático del sistema siempre los deja en cero). Mismo tope que amount.
    @PositiveOrZero
    @DecimalMax(value = "9999999999.99", message = "El número es excesivamente grande — el máximo permitido es 9,999,999,999.99")
    private BigDecimal expenses;
    // notes es TEXT (sin límite de columna) — este tope es de higiene de la app, no de la
    // base de datos. 100 y no 200/500 a propósito: es una nota corta del turno ("faltó
    // cambio", "se dañó la impresora"), no una descripción larga.
    @Size(max = 100, message = "Las notas no pueden tener más de 100 caracteres")
    private String notes;
}
