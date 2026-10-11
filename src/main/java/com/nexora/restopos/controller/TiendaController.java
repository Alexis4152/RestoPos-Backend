package com.nexora.restopos.controller;

import com.nexora.restopos.dto.ApiResponse;
import com.nexora.restopos.dto.TiendaInfoRequest;
import com.nexora.restopos.dto.WebConfigRequest;
import com.nexora.restopos.dto.TiendaRequest;
import com.nexora.restopos.dto.TiendaThemeRequest;
import com.nexora.restopos.model.Tienda;
import com.nexora.restopos.model.TiendaInfo;
import com.nexora.restopos.model.User;
import com.nexora.restopos.service.TiendaInfoService;
import com.nexora.restopos.service.TiendaLogoService;
import com.nexora.restopos.service.TiendaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Controlador de tiendas (sucursales/negocios), expuesto bajo {@code /api/tiendas}.
 * <p>
 * La administración general de tiendas (alta, baja, edición de datos base) es
 * exclusiva del SUPER_ADMIN, según el {@code @PreAuthorize} definido a nivel
 * de clase. Sin embargo, varios métodos de personalización (tema/color de
 * marca, datos fiscales y logo) declaran su propio {@code @PreAuthorize} con
 * {@code hasAnyRole('ADMIN', 'SUPER_ADMIN')}: en Spring Security el
 * {@code @PreAuthorize} de método <b>reemplaza</b> al de la clase (no se
 * combinan), por lo que en esos endpoints el ADMIN de la tienda también puede
 * operar sobre SU PROPIA tienda (el filtro de "propia tienda" lo aplica el
 * service, no la anotación).
 */
@RestController
@RequestMapping("/api/tiendas")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SUPERVISOR')")
public class TiendaController {

    private final TiendaService tiendaService;
    private final TiendaInfoService tiendaInfoService;
    private final TiendaLogoService tiendaLogoService;

    /**
     * Lista las tiendas visibles para el actor: todas si es SUPER_ADMIN (o solo las de un
     * supervisor específico, si se pasa {@code supervisorId}), o solo las suyas si es
     * SUPERVISOR.
     *
     * @param supervisorId filtro opcional por supervisor a cargo (solo aplica para SUPER_ADMIN)
     * @param actor        usuario autenticado
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Tienda>>> list(@RequestParam(required = false) Long supervisorId,
                                                           @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(tiendaService.findAll(actor, supervisorId), null));
    }

    /**
     * Obtiene el detalle de una tienda por su id. Disponible para SUPER_ADMIN y SUPERVISOR.
     *
     * @param id identificador de la tienda
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Tienda>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(tiendaService.findById(id), null));
    }

    /**
     * Da de alta una nueva tienda en el sistema. Disponible para SUPER_ADMIN y SUPERVISOR
     * (un SUPERVISOR que crea una tienda queda automáticamente a cargo de ella).
     *
     * @param req   datos de la tienda a crear
     * @param actor usuario autenticado que realiza la creación
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Tienda>> create(@Valid @RequestBody TiendaRequest req, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(tiendaService.create(req, actor), "Tienda creada"));
    }

    /**
     * Actualiza los datos base de una tienda existente (nombre y, si el actor es SUPER_ADMIN,
     * el supervisor a cargo).
     *
     * @param id    identificador de la tienda a actualizar
     * @param req   nuevos datos de la tienda
     * @param actor usuario autenticado que realiza la actualización
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Tienda>> update(@PathVariable Long id, @Valid @RequestBody TiendaRequest req, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(tiendaService.update(id, req, actor), "Tienda actualizada"));
    }

    /**
     * Desactiva (baja lógica) una tienda existente. Disponible para SUPER_ADMIN y SUPERVISOR.
     *
     * @param id    identificador de la tienda a desactivar
     * @param actor usuario autenticado que realiza la baja
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        tiendaService.deactivate(id, actor);
        return ResponseEntity.ok(ApiResponse.ok(null, "Tienda desactivada"));
    }

    // El ADMIN de una tienda también puede cambiar SU propio color de marca —
    // este método sobreescribe el @PreAuthorize de la clase.
    /**
     * Actualiza el color primario ("tema"/marca) de una tienda. Este método
     * declara su propio {@code @PreAuthorize} con {@code hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')},
     * reemplazando (no combinando) el de la clase, para que el ADMIN pueda
     * personalizar el color de SU PROPIA tienda; el service es responsable de
     * impedir que edite tiendas ajenas.
     *
     * @param id    identificador de la tienda
     * @param req   nuevo color primario
     * @param actor usuario autenticado que realiza el cambio
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    @PutMapping("/{id}/theme")
    public ResponseEntity<ApiResponse<Tienda>> updateTheme(
            @PathVariable Long id,
            @Valid @RequestBody TiendaThemeRequest req,
            @AuthenticationPrincipal User actor
    ) {
        return ResponseEntity.ok(ApiResponse.ok(tiendaService.updateTheme(id, req.getPrimaryColor(), actor), "Color actualizado"));
    }

    // Datos fiscales/de contacto y logo — igual que /theme, el ADMIN de esa tienda
    // (o SUPER_ADMIN) puede leer y editar, nunca el ADMIN de otra tienda.
    /**
     * Obtiene los datos fiscales y de contacto de una tienda. Al igual que
     * {@code /theme}, sobreescribe el {@code @PreAuthorize} de clase para
     * permitir el acceso al ADMIN de esa misma tienda, además del SUPER_ADMIN.
     *
     * @param id    identificador de la tienda
     * @param actor usuario autenticado
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    @GetMapping("/{id}/info")
    public ResponseEntity<ApiResponse<TiendaInfo>> getInfo(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(tiendaInfoService.get(id, actor), null));
    }

    /**
     * Actualiza los datos fiscales y de contacto de una tienda. Accesible para
     * el ADMIN de esa misma tienda o para SUPER_ADMIN (ver nota de
     * {@link #updateTheme} sobre la sobreescritura del {@code @PreAuthorize} de clase).
     *
     * @param id    identificador de la tienda
     * @param req   nuevos datos fiscales/de contacto
     * @param actor usuario autenticado que realiza la actualización
     */
    /** Configuración de la página web pública de la tienda (link, entregas, formas de pago). */
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    @PutMapping("/{id}/web-config")
    public ResponseEntity<ApiResponse<TiendaInfo>> updateWebConfig(
            @PathVariable Long id,
            @Valid @RequestBody WebConfigRequest req,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(tiendaInfoService.updateWebConfig(id, req, actor), "Página web actualizada"));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    @PutMapping("/{id}/info")
    public ResponseEntity<ApiResponse<TiendaInfo>> updateInfo(
            @PathVariable Long id,
            @Valid @RequestBody TiendaInfoRequest req,
            @AuthenticationPrincipal User actor
    ) {
        return ResponseEntity.ok(ApiResponse.ok(tiendaInfoService.update(id, req, actor), "Datos de la tienda actualizados"));
    }

    /**
     * Sube o reemplaza el logo de una tienda. Accesible para el ADMIN de esa
     * misma tienda o para SUPER_ADMIN.
     *
     * @param id    identificador de la tienda
     * @param file  archivo de imagen del logo (multipart/form-data)
     * @param actor usuario autenticado que realiza la carga
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    @PostMapping(value = "/{id}/logo", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<Tienda>> uploadLogo(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User actor
    ) {
        return ResponseEntity.ok(ApiResponse.ok(tiendaLogoService.upload(id, file, actor), "Logo actualizado"));
    }

    /**
     * Elimina el logo personalizado de una tienda; a partir de este cambio se
     * usará el logo por default. Accesible para el ADMIN de esa misma tienda o
     * para SUPER_ADMIN.
     *
     * @param id    identificador de la tienda
     * @param actor usuario autenticado que realiza la eliminación
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    @DeleteMapping("/{id}/logo")
    public ResponseEntity<ApiResponse<Tienda>> removeLogo(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(tiendaLogoService.remove(id, actor), "Logo eliminado, se usará el logo por default"));
    }
}
