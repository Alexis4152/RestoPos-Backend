package com.nexora.restopos.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Un error puntual de la carga masiva de productos ({@link BulkImportResult}): la fila del
 * Excel donde ocurrió (tal como la vería el usuario abriendo el archivo, encabezado = fila
 * 1) y el motivo, con el mismo texto que usaría el alta individual de un producto para ese
 * mismo error (ver {@code ProductBulkImportService}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkImportRowError {
    private int row;
    private String message;
}
