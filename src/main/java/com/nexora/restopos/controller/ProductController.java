package com.nexora.restopos.controller;

import com.nexora.restopos.dto.ApiResponse;
import com.nexora.restopos.dto.BulkImportResult;
import com.nexora.restopos.dto.InventoryAdjustRequest;
import com.nexora.restopos.dto.PageResponse;
import com.nexora.restopos.dto.ProductRequest;
import com.nexora.restopos.model.Product;
import com.nexora.restopos.model.User;
import com.nexora.restopos.service.ProductBulkImportService;
import com.nexora.restopos.service.ProductImageService;
import com.nexora.restopos.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Controlador de productos, expuesto bajo {@code /api/products}.
 * <p>
 * Reúne el catálogo de inventario de la tienda del usuario autenticado. La
 * consulta y búsqueda están disponibles desde Inventario, el Punto de Venta y,
 * en el caso de la búsqueda, también desde el widget de stock bajo del
 * Dashboard. La creación, edición, ajuste de stock y baja de productos están
 * restringidas según el rol o la sección de Inventario, como se indica en cada
 * endpoint.
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductImageService productImageService;
    private final ProductBulkImportService productBulkImportService;

    /**
     * Lista todos los productos de la tienda del usuario autenticado.
     * Accesible desde las secciones {@code MENU} o {@code ORDERS}.
     *
     * @param actor usuario autenticado; determina el filtro por tienda
     */
    // KITCHEN: cocina lo usa para marcar agotados (ver #cambiarDisponible).
    @GetMapping
    @PreAuthorize("@sectionAccess.checkAny('MENU', 'ORDERS', 'KITCHEN')")
    public ResponseEntity<ApiResponse<List<Product>>> list(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(productService.findAll(actor), null));
    }

    // usado también por el widget de stock bajo del Dashboard
    /**
     * Búsqueda paginada de productos con filtros opcionales por texto libre,
     * categoría y stock bajo. Accesible desde {@code MENU}, {@code ORDERS} o
     * {@code DASHBOARD} (este último usado por el widget de stock bajo).
     *
     * @param q          texto de búsqueda libre (nombre, SKU, etc.), opcional
     * @param categoryId id de categoría para filtrar, opcional
     * @param lowStock   si es {@code true}, limita el resultado a productos con stock bajo
     * @param page       número de página (base 0, por defecto 0)
     * @param size       tamaño de página (por defecto 20)
     * @param actor      usuario autenticado; determina el filtro por tienda
     */
    @GetMapping("/search")
    @PreAuthorize("@sectionAccess.checkAny('MENU', 'ORDERS', 'DASHBOARD')")
    public ResponseEntity<?> search(@RequestParam(required = false) String q,
                                     @RequestParam(required = false) Long categoryId,
                                     @RequestParam(required = false) Boolean lowStock,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size,
                                     @AuthenticationPrincipal User actor) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Product> result = productService.search(q, categoryId, lowStock, pageable, actor);
        return ResponseEntity.ok(ApiResponse.ok(result.getContent(), null));
    }

    /**
     * Productos más pedidos (con historial primero, luego alfabético), para el buscador
     * rápido de la toma de órdenes. Accesible desde {@code ORDERS}, {@code MENU} o
     * {@code DASHBOARD}.
     *
     * @param categoryId categoría a filtrar, o null para el ranking general
     * @param limit      cantidad máxima a devolver (por defecto 20)
     * @param actor      usuario autenticado; determina el filtro por tienda
     */
    @GetMapping("/top-selling")
    @PreAuthorize("@sectionAccess.checkAny('ORDERS', 'MENU', 'DASHBOARD')")
    public ResponseEntity<ApiResponse<List<Product>>> topSelling(@RequestParam(required = false) Long categoryId,
                                                                   @RequestParam(defaultValue = "20") int limit,
                                                                   @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(productService.topSelling(categoryId, limit, actor), null));
    }

    /**
     * Búsqueda paginada de productos activos para Inventario, con los mismos filtros que
     * {@link #search} (texto, categoría, stock bajo) pero devolviendo los metadatos
     * completos de paginación ({@code totalElements}, {@code totalPages}) — a diferencia de
     * {@link #search} (piensa en POS/Dashboard, solo regresa el arreglo), para que la
     * pantalla de Inventario pueda mostrar "página X de Y" sin traer el catálogo completo
     * de un jalón. Accesible desde {@code INVENTORY}.
     *
     * @param q          texto de búsqueda libre (nombre, código de barras), opcional
     * @param categoryId id de categoría para filtrar, opcional
     * @param lowStock   si es {@code true}, limita el resultado a productos con stock bajo
     * @param page       número de página (base 0, por defecto 0)
     * @param size       tamaño de página (por defecto 20)
     * @param actor      usuario autenticado; determina el filtro por tienda
     */
    @GetMapping("/page")
    @PreAuthorize("@sectionAccess.check('INVENTORY')")
    public ResponseEntity<ApiResponse<PageResponse<Product>>> page(@RequestParam(required = false) String q,
                                                                    @RequestParam(required = false) Long categoryId,
                                                                    @RequestParam(required = false) Boolean lowStock,
                                                                    @RequestParam(defaultValue = "0") int page,
                                                                    @RequestParam(defaultValue = "20") int size,
                                                                    @AuthenticationPrincipal User actor) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Product> result = productService.search(q, categoryId, lowStock, pageable, actor);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(result), null));
    }

    /**
     * Busca stock disponible de un producto en las tiendas "hermanas" de la del actor (el
     * mismo SUPERVISOR), sin exponer nada más de ellas. Accesible para cualquier rol con la
     * sección {@code INVENTORY} habilitada, no solo SUPER_ADMIN/SUPERVISOR — ver
     * {@link ProductService#searchSiblingStock}.
     *
     * @param q      texto de búsqueda (nombre o código de barras), obligatorio
     * @param page   número de página (base 0, por defecto 0)
     * @param size   tamaño de página (por defecto 20)
     * @param actor  usuario autenticado; determina el grupo de tiendas hermanas
     */
    @GetMapping("/sibling-stock")
    @PreAuthorize("@sectionAccess.check('INVENTORY')")
    public ResponseEntity<ApiResponse<PageResponse<Product>>> siblingStock(@RequestParam String q,
                                                                            @RequestParam(defaultValue = "0") int page,
                                                                            @RequestParam(defaultValue = "20") int size,
                                                                            @AuthenticationPrincipal User actor) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Product> result = productService.searchSiblingStock(q, actor, pageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(result), null));
    }

    /**
     * Busca un producto activo por su código de barras exacto, dentro de la tienda del
     * usuario autenticado. Pensado para el lector de código de barras de Inventario: a
     * diferencia de {@link #get}, no falla si no existe — regresa {@code data: null}, ya
     * que escanear un código desconocido es un resultado normal. Accesible desde
     * {@code INVENTORY}.
     *
     * @param barcode código de barras exacto a buscar
     * @param actor   usuario autenticado; determina el filtro por tienda
     */
    @GetMapping("/by-barcode/{barcode}")
    @PreAuthorize("@sectionAccess.check('INVENTORY')")
    public ResponseEntity<ApiResponse<Product>> getByBarcode(@PathVariable String barcode,
                                                              @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(productService.findByBarcode(barcode, actor), null));
    }

    /**
     * Obtiene el detalle de un producto por su id, dentro de la tienda del
     * usuario autenticado.
     *
     * @param id    identificador del producto
     * @param actor usuario autenticado
     */
    @GetMapping("/{id}")
    @PreAuthorize("@sectionAccess.checkAny('MENU', 'ORDERS')")
    public ResponseEntity<ApiResponse<Product>> get(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(productService.findById(id, actor), null));
    }

    /**
     * Crea un nuevo producto en la tienda del actor. Disponible para ADMIN, y para
     * SUPER_ADMIN/SUPERVISOR actuando sobre una tienda elegida.
     *
     * @param req   datos del producto a crear
     * @param actor usuario autenticado que realiza la creación
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    public ResponseEntity<ApiResponse<Product>> create(@Valid @RequestBody ProductRequest req,
                                                        @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(productService.create(req, actor), "Producto creado"));
    }

    /**
     * Actualiza los datos de un producto existente. Disponible para ADMIN, y para
     * SUPER_ADMIN/SUPERVISOR actuando sobre una tienda elegida.
     *
     * @param id    identificador del producto a actualizar
     * @param req   nuevos datos del producto
     * @param actor usuario autenticado que realiza la actualización
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    public ResponseEntity<ApiResponse<Product>> update(@PathVariable Long id,
                                                        @Valid @RequestBody ProductRequest req,
                                                        @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(productService.update(id, req, actor), "Producto actualizado"));
    }

    /**
     * Marca un platillo como agotado o de nuevo disponible ({@code {"disponible": false|true}}).
     * A diferencia de editar, no es solo para ADMIN: el mesero (sección {@code ORDERS}) lo
     * hace desde la orden, y cocina ({@code KITCHEN}) desde su pantalla, cuando ya no hay para
     * prepararlo.
     */
    @PatchMapping("/{id}/disponible")
    @PreAuthorize("@sectionAccess.checkAny('MENU', 'ORDERS', 'KITCHEN')")
    public ResponseEntity<ApiResponse<Product>> cambiarDisponible(@PathVariable Long id,
                                                                  @RequestBody java.util.Map<String, Boolean> body,
                                                                  @AuthenticationPrincipal User actor) {
        Boolean disponible = body != null ? body.get("disponible") : null;
        if (disponible == null) throw new IllegalArgumentException("Indica si el platillo está disponible");
        Product p = productService.cambiarDisponible(id, disponible, actor);
        return ResponseEntity.ok(ApiResponse.ok(p, disponible ? "\"" + p.getName() + "\" disponible de nuevo" : "\"" + p.getName() + "\" marcado como agotado"));
    }

    // cualquier rol con acceso a Menú o Inventario puede ajustar stock (no solo ADMIN)
    /**
     * Aplica un ajuste manual de stock (entrada o salida) sobre un producto.
     * A diferencia de crear/editar/eliminar, no se restringe al rol ADMIN: basta
     * con tener acceso a la sección {@code MENU} o {@code INVENTORY}.
     *
     * @param id    identificador del producto a ajustar
     * @param req   datos del ajuste (cantidad, motivo, etc.)
     * @param actor usuario autenticado que realiza el ajuste
     */
    @PostMapping("/{id}/adjust-stock")
    @PreAuthorize("@sectionAccess.checkAny('MENU', 'INVENTORY')")
    public ResponseEntity<ApiResponse<Product>> adjustStock(@PathVariable Long id,
                                                             @Valid @RequestBody InventoryAdjustRequest req,
                                                             @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(productService.adjustStock(id, req, actor), "Stock ajustado"));
    }

    /**
     * Sube (o reemplaza) la foto de un producto. Solo disponible para el rol ADMIN.
     *
     * @param id    identificador del producto
     * @param file  archivo de imagen (PNG/JPEG/WEBP, máx. 3 MB)
     * @param actor usuario autenticado que realiza la carga
     */
    @PostMapping(value = "/{id}/image", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Product>> uploadImage(@PathVariable Long id,
                                                             @RequestParam("file") MultipartFile file,
                                                             @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(productImageService.upload(id, file, actor), "Imagen actualizada"));
    }

    /**
     * Quita la foto de un producto. Solo disponible para el rol ADMIN.
     *
     * @param id    identificador del producto
     * @param actor usuario autenticado que realiza la eliminación
     */
    @DeleteMapping("/{id}/image")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Product>> removeImage(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(productImageService.remove(id, actor), "Imagen eliminada"));
    }

    /**
     * Desactiva (baja lógica) un producto existente. Disponible para ADMIN, y para
     * SUPER_ADMIN/SUPERVISOR actuando sobre una tienda elegida.
     *
     * @param id    identificador del producto a desactivar
     * @param actor usuario autenticado que realiza la baja
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        productService.deactivate(id, actor);
        return ResponseEntity.ok(ApiResponse.ok(null, "Producto desactivado"));
    }

    // ── Carga masiva de productos por Excel ────────────────────────────────────────────

    /**
     * Descarga la plantilla (.xlsx) de carga masiva, con las columnas esperadas y una fila
     * de ejemplo. Disponible para ADMIN/SUPER_ADMIN/SUPERVISOR — es de solo lectura, sin
     * ningún riesgo, a diferencia de la carga real (ver {@link #bulkImport}).
     */
    @GetMapping("/bulk-import/template")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    public ResponseEntity<byte[]> bulkImportTemplate() {
        byte[] xlsx = productBulkImportService.buildTemplate();
        return ResponseEntity.ok()
                .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .header("Content-Disposition", "attachment; filename=\"plantilla-carga-masiva-productos.xlsx\"")
                .body(xlsx);
    }

    /**
     * Procesa un archivo de carga masiva de productos, creando uno por cada fila válida y
     * reportando el resto como errores (sin tumbar la carga completa por una fila mala).
     * Restringido a SUPER_ADMIN.
     */
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping(value = "/bulk-import", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<BulkImportResult>> bulkImport(@RequestParam("file") MultipartFile file,
                                                                     @AuthenticationPrincipal User actor) {
        BulkImportResult result = productBulkImportService.importFile(file, actor);
        String message = result.getErrorCount() == 0
                ? result.getCreated() + " producto(s) creado(s) correctamente"
                : result.getCreated() + " producto(s) creado(s), " + result.getErrorCount() + " con error";
        return ResponseEntity.ok(ApiResponse.ok(result, message));
    }
}
