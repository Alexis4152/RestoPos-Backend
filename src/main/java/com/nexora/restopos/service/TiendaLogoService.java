package com.nexora.restopos.service;

import com.nexora.restopos.model.Tienda;
import com.nexora.restopos.model.User;
import com.nexora.restopos.repository.TiendaRepository;
import com.nexora.restopos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Administra la subida y borrado del logo de marca de cada tienda.
 *
 * <p>El archivo se guarda en disco local bajo {@code app.uploads.dir}/logos (nombre único
 * {@code tienda-<id>-<uuid>.<ext>}) y su ruta pública queda en {@code Tienda.logoPath}, servida
 * vía {@code /uploads/**}. La validación (PNG/JPEG/WEBP por contenido real, máx. 3&nbsp;MB) y
 * el guardado viven en {@link ImageStorageService}. Solo el ADMIN de la tienda o un SUPER_ADMIN
 * pueden subir o quitar el logo. Si una tienda no tiene logo, el frontend y el ticket en PDF
 * ({@link TicketPdfService}) usan el de Nexora.</p>
 */
@Service
@RequiredArgsConstructor
public class TiendaLogoService {

    private final TiendaRepository tiendaRepository;
    private final TenantScope tenantScope;
    private final ImageStorageService imageStorage;

    /**
     * Sube (o reemplaza) el logo de una tienda: guarda el archivo nuevo, borra el anterior
     * (best effort) y actualiza {@code Tienda.logoPath}.
     *
     * @throws IllegalArgumentException si el archivo viene vacío, no es una imagen PNG/JPG/WEBP
     *         real o excede los 3 MB
     * @throws AccessDeniedException si el actor no puede administrar esa tienda
     */
    @Transactional
    public Tienda upload(Long tiendaId, MultipartFile file, User actor) {
        checkAccess(tiendaId, actor);
        Tienda tienda = findTienda(tiendaId);
        String ruta = imageStorage.guardar(file, "logos", "tienda-" + tiendaId, "El logo");
        imageStorage.borrar(tienda.getLogoPath());
        tienda.setLogoPath(ruta);
        tienda.setUpdatedBy(actor);
        return tiendaRepository.save(tienda);
    }

    /** Quita el logo (borra el archivo, best effort) y vuelve al logo por defecto de Nexora. */
    @Transactional
    public Tienda remove(Long tiendaId, User actor) {
        checkAccess(tiendaId, actor);
        Tienda tienda = findTienda(tiendaId);
        imageStorage.borrar(tienda.getLogoPath());
        tienda.setLogoPath(null);
        tienda.setUpdatedBy(actor);
        return tiendaRepository.save(tienda);
    }

    private Tienda findTienda(Long tiendaId) {
        return tiendaRepository.findById(tiendaId)
                .orElseThrow(() -> new IllegalArgumentException("Tienda no encontrada: " + tiendaId));
    }

    private void checkAccess(Long tiendaId, User actor) {
        if (!tenantScope.canManageTienda(actor, tiendaId)) {
            throw new AccessDeniedException("No tienes permiso sobre el logo de esta tienda");
        }
    }
}
