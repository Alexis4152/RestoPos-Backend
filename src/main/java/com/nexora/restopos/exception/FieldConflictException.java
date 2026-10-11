package com.nexora.restopos.exception;

import lombok.Getter;

/**
 * Error de negocio asociado a un campo específico del formulario (ej. "ese código de barras
 * ya existe", "stock insuficiente"), para que el frontend lo muestre justo debajo del input
 * correspondiente en vez de como un mensaje genérico — ver
 * {@link GlobalExceptionHandler#handleFieldConflict}, que lo traduce a la misma forma
 * {@code data: {campo: mensaje}} que ya usan los errores de validación de {@code @Valid}.
 */
@Getter
public class FieldConflictException extends RuntimeException {
    private final String field;

    public FieldConflictException(String field, String message) {
        super(message);
        this.field = field;
    }
}
