package com.nexora.restopos.service;

import com.nexora.restopos.dto.BulkImportResult;
import com.nexora.restopos.dto.BulkImportRowError;
import com.nexora.restopos.dto.ProductRequest;
import com.nexora.restopos.exception.FieldConflictException;
import com.nexora.restopos.model.Category;
import com.nexora.restopos.model.Tienda;
import com.nexora.restopos.model.User;
import com.nexora.restopos.security.TenantScope;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Carga masiva de productos vía un archivo Excel (.xlsx), disponible solo para SUPER_ADMIN
 * ({@code ProductController#bulkImport}) — la plantilla ({@link #buildTemplate()}) sí la
 * puede descargar cualquier ADMIN/SUPER_ADMIN/SUPERVISOR, igual que el resto de acciones de
 * catálogo.
 * <p>
 * Procesamiento síncrono, dentro del mismo request HTTP: no hay Spring Batch ni ningún job
 * de fondo — para el volumen real de un catálogo de tienda (cientos o unos pocos miles de
 * filas) agregar esa infraestructura sería puro peso arquitectónico sin beneficio real.
 * Cada fila válida se crea reutilizando {@link ProductService#create}, así la carga masiva
 * queda sujeta EXACTAMENTE a las mismas reglas (límites de campo, código de barras único)
 * que el alta individual, sin duplicar esa lógica.
 * <p>
 * Un código de barras duplicado (contra la base de datos, o repetido dentro del mismo
 * archivo) se reporta como error de esa fila — nunca actualiza el producto existente.
 */
@Service
@RequiredArgsConstructor
public class ProductBulkImportService {

    // Con más errores que esto, la lista ya no cabe cómoda en un modal — de ahí en
    // adelante se ofrece el Excel de errores completo en vez de seguir alargando la lista.
    private static final int MAX_INLINE_ERRORS = 10;

    private static final String[] HEADERS = {
            "Nombre*", "Descripción", "Código de barras", "Precio*", "Costo",
            "Stock", "Stock mínimo", "Unidad", "Categoría*",
    };

    private final ProductService productService;
    private final CategoryService categoryService;
    private final TenantScope tenantScope;
    private final Validator validator;

    /**
     * Genera la plantilla (.xlsx) con las columnas esperadas y una fila de ejemplo, para
     * que quien vaya a llenarla no tenga que adivinar el formato.
     *
     * @return el archivo .xlsx generado, en bytes
     */
    public byte[] buildTemplate() {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Productos");
            CellStyle header = headerStyle(wb);
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(header);
            }
            Row example = sheet.createRow(1);
            String[] exampleValues = {
                    "Hamburguesa clásica", "Con queso, lechuga y tomate", "7501234567890",
                    "129.00", "55.00", "0", "0", "pieza", "Platillos",
            };
            for (int i = 0; i < exampleValues.length; i++) {
                example.createCell(i).setCellValue(exampleValues[i]);
            }
            for (int i = 0; i < HEADERS.length; i++) sheet.autoSizeColumn(i);
            return toBytes(wb);
        } catch (IOException e) {
            throw new com.nexora.restopos.exception.OperacionFallidaException("No se pudo generar la plantilla de carga masiva", e);
        }
    }

    /**
     * Procesa el archivo subido: crea un producto por cada fila válida y reporta el resto
     * como errores, sin tumbar la carga completa por una sola fila mala.
     *
     * @param file archivo .xlsx subido, con el mismo formato que {@link #buildTemplate()}
     * @param actor usuario autenticado (SUPER_ADMIN); determina la tienda sobre la que actúa
     * @return el resumen de la carga (creados, errores, y el reporte descargable si aplica)
     * @throws IllegalStateException si el actor no tiene ninguna tienda elegida para actuar
     */
    public BulkImportResult importFile(MultipartFile file, User actor) {
        Tienda tienda = tenantScope.tiendaForWrite(actor);
        if (tienda == null) {
            throw new IllegalStateException("Elige una tienda para poder cargar productos");
        }

        // Nombre de categoría -> Category, sin distinguir mayúsculas/minúsculas (mismo
        // criterio que el resto de la app usa para nombres únicos).
        Map<String, Category> categoriesByName = categoryService.findAll(actor).stream()
                .collect(Collectors.toMap(c -> c.getName().trim().toLowerCase(), c -> c, (a, b) -> a));

        int totalRows = 0;
        int created = 0;
        List<BulkImportRowError> errors = new ArrayList<>();
        // fila original (tal como se leyó) + el mensaje de error, para reconstruir el
        // Excel de errores exactamente como llegó, solo con la columna extra.
        List<Object[]> errorRows = new ArrayList<>();
        Set<String> seenBarcodes = new HashSet<>();

        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isBlankRow(row, formatter)) continue;
                totalRows++;
                int excelRowNumber = r + 1; // como lo ve un humano abriendo el archivo (encabezado = fila 1)

                String[] rawValues = readRowValues(row, formatter);
                try {
                    ProductRequest req = mapToRequest(rawValues, categoriesByName);
                    Set<ConstraintViolation<ProductRequest>> violations = validator.validate(req);
                    if (!violations.isEmpty()) {
                        throw new IllegalArgumentException(violations.stream()
                                .map(ConstraintViolation::getMessage)
                                .collect(Collectors.joining("; ")));
                    }
                    if (req.getBarcode() != null && !seenBarcodes.add(req.getBarcode())) {
                        throw new IllegalArgumentException("Código de barras repetido dentro del mismo archivo");
                    }
                    productService.create(req, actor);
                    created++;
                } catch (FieldConflictException | IllegalArgumentException | IllegalStateException ex) {
                    errors.add(new BulkImportRowError(excelRowNumber, ex.getMessage()));
                    errorRows.add(new Object[]{rawValues, ex.getMessage()});
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo leer el archivo — ¿es un Excel (.xlsx) válido?");
        }

        BulkImportResult.BulkImportResultBuilder result = BulkImportResult.builder()
                .totalRows(totalRows)
                .created(created)
                .errorCount(errors.size())
                .errors(errors.size() > MAX_INLINE_ERRORS ? errors.subList(0, MAX_INLINE_ERRORS) : errors);

        if (errors.size() > MAX_INLINE_ERRORS) {
            result.errorReportBase64(Base64.getEncoder().encodeToString(buildErrorReport(errorRows)));
        }
        return result.build();
    }

    /** true si la fila está completamente vacía (huecos entre filas con datos, comunes al editar un Excel a mano). */
    private boolean isBlankRow(Row row, DataFormatter formatter) {
        for (Cell cell : row) {
            if (!formatter.formatCellValue(cell).isBlank()) return false;
        }
        return true;
    }

    private String[] readRowValues(Row row, DataFormatter formatter) {
        String[] values = new String[HEADERS.length];
        for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = row.getCell(i);
            values[i] = cell != null ? formatter.formatCellValue(cell).trim() : "";
        }
        return values;
    }

    /**
     * Traduce una fila cruda del Excel a un {@link ProductRequest}, listo para pasar por la
     * misma validación (`Validator`) y el mismo {@link ProductService#create} que usa el
     * alta individual. Los números vienen como texto (columna con formato de texto o de
     * número, `DataFormatter` normaliza ambos) — un valor no numérico en precio/costo/stock
     * se reporta como error de esa fila, igual que cualquier otro dato inválido.
     */
    private ProductRequest mapToRequest(String[] v, Map<String, Category> categoriesByName) {
        if (v[0].isBlank()) {
            throw new IllegalArgumentException("El nombre es obligatorio");
        }
        if (v[3].isBlank()) {
            throw new IllegalArgumentException("El precio es obligatorio");
        }
        ProductRequest req = new ProductRequest();
        req.setName(v[0]);
        req.setDescription(blankToNull(v[1]));
        req.setBarcode(blankToNull(v[2]));
        req.setPrice(parseDecimal(v[3], "precio"));
        req.setCost(v[4].isBlank() ? BigDecimal.ZERO : parseDecimal(v[4], "costo"));
        req.setStock(v[5].isBlank() ? 0 : parseInt(v[5], "stock"));
        req.setMinStock(v[6].isBlank() ? 0 : parseInt(v[6], "stock mínimo"));
        req.setUnit(v[7].isBlank() ? "pieza" : v[7]);
        // La carga masiva es la plantilla de Inventario (trae stock y mínimo): lleva inventario.
        req.setControlaInventario(true);

        String categoryName = v[8].trim();
        if (categoryName.isEmpty()) {
            throw new IllegalArgumentException("La categoría es obligatoria");
        }
        Category category = categoriesByName.get(categoryName.toLowerCase());
        if (category == null) {
            throw new IllegalArgumentException("No existe la categoría \"" + categoryName + "\"");
        }
        req.setCategoryId(category.getId());
        return req;
    }

    private String blankToNull(String v) {
        return v.isBlank() ? null : v;
    }

    private BigDecimal parseDecimal(String raw, String label) {
        try {
            return new BigDecimal(raw.replace(",", "").trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("El " + label + " \"" + raw + "\" no es un número válido");
        }
    }

    private Integer parseInt(String raw, String label) {
        try {
            return new BigDecimal(raw.replace(",", "").trim()).intValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw new IllegalArgumentException("El " + label + " \"" + raw + "\" no es un número entero válido");
        }
    }

    /**
     * Reconstruye el Excel de errores: las mismas filas/columnas que se subieron, solo con
     * una columna "Error" agregada al final de cada una que falló.
     */
    private byte[] buildErrorReport(List<Object[]> errorRows) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Errores");
            CellStyle header = headerStyle(wb);
            CellStyle errorStyle = errorStyle(wb);
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(header);
            }
            Cell errorHeader = headerRow.createCell(HEADERS.length);
            errorHeader.setCellValue("Error");
            errorHeader.setCellStyle(header);

            int r = 1;
            for (Object[] entry : errorRows) {
                String[] values = (String[]) entry[0];
                String message = (String) entry[1];
                Row row = sheet.createRow(r++);
                for (int i = 0; i < values.length; i++) {
                    row.createCell(i).setCellValue(values[i]);
                }
                Cell errorCell = row.createCell(values.length);
                errorCell.setCellValue(message);
                errorCell.setCellStyle(errorStyle);
            }
            for (int i = 0; i <= HEADERS.length; i++) sheet.autoSizeColumn(i);
            return toBytes(wb);
        } catch (IOException e) {
            throw new com.nexora.restopos.exception.OperacionFallidaException("No se pudo generar el reporte de errores", e);
        }
    }

    private byte[] toBytes(Workbook wb) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        return out.toByteArray();
    }

    private CellStyle headerStyle(Workbook wb) {
        Font f = wb.createFont();
        f.setBold(true);
        f.setColor(IndexedColors.WHITE.getIndex());
        CellStyle s = wb.createCellStyle();
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }

    private CellStyle errorStyle(Workbook wb) {
        Font f = wb.createFont();
        f.setColor(IndexedColors.RED.getIndex());
        CellStyle s = wb.createCellStyle();
        s.setFont(f);
        s.setWrapText(true);
        return s;
    }
}
