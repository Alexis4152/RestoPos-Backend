package com.nexora.restopos.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Resultado de una carga masiva de productos por Excel ({@code POST
 * /api/products/bulk-import}, ver {@code ProductBulkImportService}).
 * <p>
 * {@code errors} trae como máximo las primeras {@code MAX_INLINE_ERRORS} filas con error,
 * pensadas para mostrarse directo en el modal de carga; si hubo más errores de los que caben
 * ahí, {@code errorReportBase64} trae el Excel completo (mismas filas que se subieron, con
 * una columna "Error" agregada solo en las que fallaron) codificado en base64, para que el
 * frontend dispare su descarga sin necesitar una segunda petición — la carga es síncrona y
 * no queda ningún job guardado del que volver a pedirlo después.
 */
@Data
@Builder
public class BulkImportResult {
    private int totalRows;
    private int created;
    private int errorCount;
    private List<BulkImportRowError> errors;
    private String errorReportBase64;
}
