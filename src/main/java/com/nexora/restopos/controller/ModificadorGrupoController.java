package com.nexora.restopos.controller;

import com.nexora.restopos.dto.ApiResponse;
import com.nexora.restopos.dto.ModificadorGrupoCopiableView;
import com.nexora.restopos.dto.ModificadorGrupoRequest;
import com.nexora.restopos.model.ModificadorGrupo;
import com.nexora.restopos.model.User;
import com.nexora.restopos.service.ModificadorGrupoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Grupos de modificadores de un producto (ej. "Término de cocción", "Extras"), expuestos bajo
 * {@code /api/products/{productoId}/modificador-grupos} (listar/crear) y
 * {@code /api/modificador-grupos/{id}} (editar/eliminar). La lectura está disponible desde
 * {@code MENU} (administración del catálogo) y {@code ORDERS}/{@code TAKEAWAY} (toma de
 * órdenes, para saber qué mostrarle al mesero al agregar el producto); la escritura, a un
 * nivel de administrador (ADMIN, o SUPER_ADMIN/SUPERVISOR actuando sobre una tienda elegida).
 */
@RestController
@RequiredArgsConstructor
public class ModificadorGrupoController {

    private final ModificadorGrupoService modificadorGrupoService;

    @GetMapping("/api/products/{productoId}/modificador-grupos")
    @PreAuthorize("@sectionAccess.checkAny('MENU', 'ORDERS', 'TAKEAWAY')")
    public ResponseEntity<ApiResponse<List<ModificadorGrupo>>> list(@PathVariable Long productoId,
                                                                     @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(modificadorGrupoService.findAllByProducto(productoId, actor), null));
    }

    /** Grupos de otros productos de la misma categoría, para copiarlos a este — ver ModificadorGrupoService#findCopiablesDeLaMismaCategoria. */
    @GetMapping("/api/products/{productoId}/modificador-grupos/copiables")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    public ResponseEntity<ApiResponse<List<ModificadorGrupoCopiableView>>> copiables(@PathVariable Long productoId,
                                                                                      @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(modificadorGrupoService.findCopiablesDeLaMismaCategoria(productoId, actor), null));
    }

    @PostMapping("/api/products/{productoId}/modificador-grupos")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    public ResponseEntity<ApiResponse<ModificadorGrupo>> create(@PathVariable Long productoId,
                                                                 @Valid @RequestBody ModificadorGrupoRequest req,
                                                                 @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(modificadorGrupoService.create(productoId, req, actor), "Grupo creado"));
    }

    /** Nuevo orden de los grupos del producto: lista con los ids de todos sus grupos activos, el primero arriba. */
    @PutMapping("/api/products/{productoId}/modificador-grupos/orden")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    public ResponseEntity<ApiResponse<List<ModificadorGrupo>>> reordenar(@PathVariable Long productoId,
                                                                          @RequestBody List<Long> grupoIds,
                                                                          @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(modificadorGrupoService.reordenar(productoId, grupoIds, actor), "Orden guardado"));
    }

    @PutMapping("/api/modificador-grupos/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    public ResponseEntity<ApiResponse<ModificadorGrupo>> update(@PathVariable Long id,
                                                                 @Valid @RequestBody ModificadorGrupoRequest req,
                                                                 @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(modificadorGrupoService.update(id, req, actor), "Grupo actualizado"));
    }

    @DeleteMapping("/api/modificador-grupos/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        modificadorGrupoService.delete(id, actor);
        return ResponseEntity.ok(ApiResponse.ok(null, "Grupo eliminado"));
    }
}
