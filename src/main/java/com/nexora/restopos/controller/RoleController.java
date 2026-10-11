package com.nexora.restopos.controller;

import com.nexora.restopos.dto.ApiResponse;
import com.nexora.restopos.dto.RoleRequest;
import com.nexora.restopos.model.Role;
import com.nexora.restopos.model.User;
import com.nexora.restopos.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador de roles, expuesto bajo {@code /api/roles}.
 * <p>
 * Permite administrar los roles de la tienda del usuario autenticado y las
 * {@code AppSection} (secciones de la aplicación) habilitadas para cada uno,
 * que es precisamente lo que alimenta el RBAC dinámico usado por
 * {@code @sectionAccess} en el resto de los controladores. Todos los métodos
 * requieren acceso a la sección {@code ROLES}, según el {@code @PreAuthorize}
 * definido a nivel de clase.
 */
@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
@PreAuthorize("@sectionAccess.check('ROLES')")
public class RoleController {

    private final RoleService roleService;

    /**
     * Lista todos los roles de la tienda del usuario autenticado.
     *
     * @param actor usuario autenticado; determina el filtro por tienda
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Role>>> list(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(roleService.findAll(actor), null));
    }

    /**
     * Obtiene el detalle de un rol por su id, dentro de la tienda del usuario
     * autenticado.
     *
     * @param id    identificador del rol
     * @param actor usuario autenticado
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Role>> get(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(roleService.findById(id, actor), null));
    }

    /**
     * Crea un nuevo rol, incluyendo las secciones de la aplicación que tendrá
     * habilitadas.
     *
     * @param req   datos del rol a crear (nombre, secciones habilitadas, etc.)
     * @param actor usuario autenticado que realiza la creación
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Role>> create(@Valid @RequestBody RoleRequest req, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(roleService.create(req, actor), "Rol creado"));
    }

    /**
     * Actualiza un rol existente, incluidas las secciones de la aplicación
     * habilitadas para él.
     *
     * @param id    identificador del rol a actualizar
     * @param req   nuevos datos del rol
     * @param actor usuario autenticado que realiza la actualización
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Role>> update(@PathVariable Long id, @Valid @RequestBody RoleRequest req,
                                                     @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(roleService.update(id, req, actor), "Rol actualizado"));
    }

    /**
     * Elimina un rol existente.
     *
     * @param id    identificador del rol a eliminar
     * @param actor usuario autenticado que realiza la eliminación
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        roleService.delete(id, actor);
        return ResponseEntity.ok(ApiResponse.ok(null, "Rol eliminado"));
    }
}
