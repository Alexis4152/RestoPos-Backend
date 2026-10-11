package com.nexora.restopos.controller;

import com.nexora.restopos.dto.ApiResponse;
import com.nexora.restopos.dto.CategoryAvailabilityModeRequest;
import com.nexora.restopos.dto.CategoryRequest;
import com.nexora.restopos.model.Category;
import com.nexora.restopos.model.User;
import com.nexora.restopos.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador de categorías de producto, expuesto bajo {@code /api/categories}.
 * <p>
 * La consulta está disponible tanto desde Inventario como desde el Punto de
 * Venta (para poder filtrar productos por categoría al vender); la creación,
 * edición y eliminación quedan reservadas a un nivel de administrador (ADMIN, o
 * SUPER_ADMIN/SUPERVISOR actuando sobre una tienda elegida). Todas las operaciones
 * están acotadas a la tienda del actor.
 */
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * Lista todas las categorías de la tienda del usuario autenticado.
     * Accesible desde las secciones {@code MENU} o {@code ORDERS}.
     *
     * @param actor usuario autenticado; determina el filtro por tienda
     */
    @GetMapping
    @PreAuthorize("@sectionAccess.checkAny('MENU', 'ORDERS')")
    public ResponseEntity<ApiResponse<List<Category>>> list(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.findAll(actor), null));
    }

    /**
     * Obtiene el detalle de una categoría por su id, dentro de la tienda del
     * usuario autenticado.
     *
     * @param id    identificador de la categoría
     * @param actor usuario autenticado
     */
    @GetMapping("/{id}")
    @PreAuthorize("@sectionAccess.checkAny('MENU', 'ORDERS')")
    public ResponseEntity<ApiResponse<Category>> get(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.findById(id, actor), null));
    }

    /**
     * Crea una nueva categoría en la tienda del actor. Disponible para ADMIN, y para
     * SUPER_ADMIN/SUPERVISOR actuando sobre una tienda elegida.
     *
     * @param req   datos de la categoría a crear
     * @param actor usuario autenticado que realiza la creación
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    public ResponseEntity<ApiResponse<Category>> create(@Valid @RequestBody CategoryRequest req, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.create(req, actor), "Categoría creada"));
    }

    /**
     * Actualiza los datos de una categoría existente. Disponible para ADMIN, y para
     * SUPER_ADMIN/SUPERVISOR actuando sobre una tienda elegida.
     *
     * @param id    identificador de la categoría a actualizar
     * @param req   nuevos datos de la categoría
     * @param actor usuario autenticado que realiza la actualización
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    public ResponseEntity<ApiResponse<Category>> update(@PathVariable Long id,
                                                         @Valid @RequestBody CategoryRequest req,
                                                         @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.update(id, req, actor), "Categoría actualizada"));
    }

    /**
     * Elimina una categoría existente. Disponible para ADMIN, y para SUPER_ADMIN/SUPERVISOR
     * actuando sobre una tienda elegida.
     *
     * @param id    identificador de la categoría a eliminar
     * @param actor usuario autenticado que realiza la eliminación
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        categoryService.delete(id, actor);
        return ResponseEntity.ok(ApiResponse.ok(null, "Categoría eliminada"));
    }

    // A diferencia de create/update/delete (ADMIN), este endpoint es accesible desde MENU u
    // ORDERS para que un mesero pueda forzar visible/oculta una categoría al momento (ej. se
    // acabó el insumo del desayuno antes de que terminara su horario) sin necesitar el rol ADMIN.
    /**
     * Cambia únicamente el modo de disponibilidad de una categoría (AUTO/FORCE_VISIBLE/
     * FORCE_HIDDEN). Accesible desde las secciones {@code MENU}, {@code ORDERS} o
     * {@code TAKEAWAY} (las mismas desde las que se puede llegar a tomar una orden).
     *
     * @param id    identificador de la categoría a modificar
     * @param req   nuevo modo de disponibilidad
     * @param actor usuario autenticado que realiza el cambio
     */
    @PutMapping("/{id}/availability-mode")
    @PreAuthorize("@sectionAccess.checkAny('MENU', 'ORDERS', 'TAKEAWAY')")
    public ResponseEntity<ApiResponse<Category>> updateAvailabilityMode(@PathVariable Long id,
                                                                         @Valid @RequestBody CategoryAvailabilityModeRequest req,
                                                                         @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.updateAvailabilityMode(id, req.getMode(), actor), "Disponibilidad actualizada"));
    }
}
