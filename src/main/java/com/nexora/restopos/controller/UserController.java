package com.nexora.restopos.controller;

import com.nexora.restopos.dto.ApiResponse;
import com.nexora.restopos.dto.UserRequest;
import com.nexora.restopos.model.User;
import com.nexora.restopos.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Controlador de usuarios, expuesto bajo {@code /api/users}.
 * <p>
 * Administra los usuarios (cajeros, meseros, administradores, etc.) de la tienda del
 * usuario autenticado, incluyendo su asignación de rol. Todos los métodos requieren acceso
 * a la sección {@code USERS} (el {@code @PreAuthorize} de la clase); dar de alta, editar y
 * desactivar usuarios además exigen el rol {@code ADMIN}, {@code SUPERVISOR} o {@code
 * SUPER_ADMIN} (ver el {@code @PreAuthorize} de cada uno de esos tres métodos, que repite
 * la sección porque un {@code @PreAuthorize} a nivel de método reemplaza al de la clase en
 * vez de sumarse) — antes cualquiera con la sección {@code USERS} habilitada (ej. un
 * mesero) podía crear/editar/desactivar usuarios. La jerarquía real de qué rol puede
 * asignar qué otro rol la aplica {@link UserService} (ver {@code assertCanAssignRole}), no
 * esta anotación.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("@sectionAccess.check('USERS')")
public class UserController {

    private final UserService userService;

    /**
     * Lista los usuarios de la tienda del usuario autenticado, con filtros
     * opcionales por rango de fecha de alta, nombre, email, rol y estado activo.
     *
     * @param from     fecha/hora inicial del rango de alta (opcional)
     * @param to       fecha/hora final del rango de alta (opcional)
     * @param name     nombre a filtrar (opcional)
     * @param email    email a filtrar (opcional)
     * @param roleId   id de rol a filtrar (opcional)
     * @param isActive filtra por usuarios activos/inactivos (opcional)
     * @param actor    usuario autenticado; determina el filtro por tienda
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<User>>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) Long roleId,
            @RequestParam(required = false) Boolean isActive,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(userService.findAll(from, to, name, email, roleId, isActive, actor), null));
    }

    /**
     * Obtiene el detalle de un usuario por su id, dentro de la tienda del
     * usuario autenticado.
     *
     * @param id    identificador del usuario
     * @param actor usuario autenticado
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<User>> get(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(userService.findById(id, actor), null));
    }

    /**
     * Crea un nuevo usuario en la tienda del usuario autenticado.
     *
     * @param req   datos del usuario a crear (incluye el rol asignado)
     * @param actor usuario autenticado que realiza la creación
     */
    // hasAnyRole explícito aquí (además del @sectionAccess de la clase, que un
    // @PreAuthorize a nivel de método REEMPLAZA en vez de sumar) porque cualquiera con la
    // sección USERS habilitada podía crear/editar/desactivar usuarios — debe ser solo
    // ADMIN/SUPER_ADMIN/SUPERVISOR.
    @PostMapping
    @PreAuthorize("@sectionAccess.check('USERS') and hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    public ResponseEntity<ApiResponse<User>> create(@Valid @RequestBody UserRequest req, @AuthenticationPrincipal User actor) {
        // Se calcula ANTES de crear: userService.create() reactiva en vez de crear cuando el
        // correo ya pertenecía a un usuario desactivado (ver UserService#reactivate) — después
        // de la llamada ya no hay forma de distinguir "se creó" de "se reactivó".
        boolean reactivating = userService.isReactivatableEmail(req.getEmail());
        User saved = userService.create(req, actor);
        String message = reactivating
                ? "Ese correo ya tenía un usuario desactivado — se reactivó en vez de crear uno nuevo"
                : "Usuario creado";
        return ResponseEntity.ok(ApiResponse.ok(saved, message));
    }

    /**
     * Actualiza los datos de un usuario existente.
     *
     * @param id    identificador del usuario a actualizar
     * @param req   nuevos datos del usuario
     * @param actor usuario autenticado que realiza la actualización
     */
    @PutMapping("/{id}")
    @PreAuthorize("@sectionAccess.check('USERS') and hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    public ResponseEntity<ApiResponse<User>> update(@PathVariable Long id,
                                                     @Valid @RequestBody UserRequest req,
                                                     @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(userService.update(id, req, actor), "Usuario actualizado"));
    }

    /**
     * Desactiva (baja lógica) un usuario existente.
     *
     * @param id    identificador del usuario a desactivar
     * @param actor usuario autenticado que realiza la baja
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@sectionAccess.check('USERS') and hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        userService.deactivate(id, actor);
        return ResponseEntity.ok(ApiResponse.ok(null, "Usuario desactivado"));
    }
}
