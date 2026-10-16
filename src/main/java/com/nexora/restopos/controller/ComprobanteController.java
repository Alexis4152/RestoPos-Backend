package com.nexora.restopos.controller;

import com.nexora.restopos.model.User;
import com.nexora.restopos.service.OrdenService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;

/**
 * Foto del comprobante de transferencia que subió el cliente de un pedido web. No es pública
 * (vive en app.private-uploads.dir, fuera de /uploads/**): solo la ve el personal con sesión que
 * cobra o supervisa — repartidor (DELIVERY), caja (CASH_CUTS / SALES), ADMIN y SUPERVISOR — y
 * solo de órdenes de su tienda (lo valida {@link OrdenService#archivoComprobante}).
 */
@RestController
@RequestMapping("/api/comprobantes")
@RequiredArgsConstructor
@PreAuthorize("@sectionAccess.checkAny('DELIVERY', 'CASH_CUTS', 'SALES') or hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
public class ComprobanteController {

    private final OrdenService ordenService;

    @GetMapping("/orden/{id}")
    public ResponseEntity<Resource> comprobante(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        Path archivo = ordenService.archivoComprobante(id, actor);
        String nombre = archivo.getFileName().toString().toLowerCase();
        MediaType tipo = nombre.endsWith(".png") ? MediaType.IMAGE_PNG
                : nombre.endsWith(".webp") ? MediaType.parseMediaType("image/webp")
                : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok()
                .contentType(tipo)
                // Dato sensible: que ningún caché intermedio ni el navegador lo guarde.
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(archivo));
    }
}
