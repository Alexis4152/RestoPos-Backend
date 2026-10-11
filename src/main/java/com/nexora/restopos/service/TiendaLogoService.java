package com.nexora.restopos.service;

import com.nexora.restopos.model.Tienda;
import com.nexora.restopos.model.User;
import com.nexora.restopos.repository.TiendaRepository;
import com.nexora.restopos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

// Guarda el logo de cada tienda en disco local (dev: carpeta del proyecto backend;
// prod: la misma ruta pero apuntando al disco del server) y deja la ruta pública
// (servida vía /uploads/**, ver StaticResourceConfig) en Tienda.logoPath.
// Si una tienda no sube logo, logoPath se queda null y el frontend usa el de Nexora.
/**
 * Administra la subida y borrado del logo de marca de cada tienda.
 *
 * <p>El archivo se guarda en disco local, bajo {@code app.uploads.dir}/logos, con un
 * nombre único ({@code tienda-<id>-<uuid>.<ext>}) para evitar colisiones entre subidas.
 * Solo se acepta PNG/JPEG/WEBP hasta 3&nbsp;MB. Solo el ADMIN de la tienda o un
 * SUPER_ADMIN pueden subir o quitar el logo. El logo también se usa en el ticket de
 * venta en PDF ({@link TicketPdfService}), que cae al logo por defecto de Nexora si la
 * tienda no tiene uno propio.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TiendaLogoService {

    private static final Set<String> ALLOWED_TYPES = Set.of("image/png", "image/jpeg", "image/webp");
    private static final long MAX_SIZE_BYTES = 3L * 1024 * 1024; // 3 MB

    private final TiendaRepository tiendaRepository;
    private final TenantScope tenantScope;

    @Value("${app.uploads.dir}")
    private String uploadsDir;

    /**
     * Sube (o reemplaza) el logo de una tienda.
     *
     * <p>Valida tipo de archivo (PNG/JPEG/WEBP) y tamaño máximo (3&nbsp;MB), guarda el
     * archivo nuevo con un nombre único, borra el archivo del logo anterior si existía
     * (best effort — un fallo al borrar solo se registra en el log) y actualiza
     * {@code Tienda.logoPath} con la ruta pública servida vía {@code /uploads/**}.</p>
     *
     * @param tiendaId id de la tienda
     * @param file archivo de imagen subido (PNG, JPEG o WEBP, máx. 3 MB)
     * @param actor usuario que sube el logo; debe poder administrar esa tienda; queda
     *              registrado como {@code updatedBy}
     * @return la tienda con {@code logoPath} actualizado
     * @throws IllegalArgumentException si el archivo viene vacío, no es de un tipo
     *         permitido o excede el tamaño máximo
     * @throws org.springframework.security.access.AccessDeniedException si el actor no
     *         tiene permiso sobre esa tienda
     * @throws RuntimeException si falla la escritura del archivo a disco
     */
    @Transactional
    public Tienda upload(Long tiendaId, MultipartFile file, User actor) {
        checkAccess(tiendaId, actor);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Selecciona un archivo de imagen");
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("El logo debe ser PNG, JPG o WEBP");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("El logo no debe pesar más de 3 MB");
        }

        Tienda tienda = findTienda(tiendaId);
        try {
            Path logosDir = Paths.get(uploadsDir, "logos");
            Files.createDirectories(logosDir);

            String ext = extensionFor(file.getContentType());
            String filename = "tienda-" + tiendaId + "-" + UUID.randomUUID() + ext;
            Path target = logosDir.resolve(filename);
            file.transferTo(target);

            deleteExistingLogoFile(tienda.getLogoPath());
            tienda.setLogoPath("/uploads/logos/" + filename);
            tienda.setUpdatedBy(actor);
            return tiendaRepository.save(tienda);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo guardar el logo", e);
        }
    }

    /**
     * Quita el logo de una tienda: borra el archivo en disco (best effort) y limpia
     * {@code Tienda.logoPath}. Tras esto, el frontend vuelve a mostrar el logo por
     * defecto de Nexora.
     *
     * @param tiendaId id de la tienda
     * @param actor usuario que quita el logo; debe poder administrar esa tienda; queda
     *              registrado como {@code updatedBy}
     * @return la tienda con {@code logoPath} en null
     * @throws org.springframework.security.access.AccessDeniedException si el actor no
     *         tiene permiso sobre esa tienda
     */
    @Transactional
    public Tienda remove(Long tiendaId, User actor) {
        checkAccess(tiendaId, actor);
        Tienda tienda = findTienda(tiendaId);
        deleteExistingLogoFile(tienda.getLogoPath());
        tienda.setLogoPath(null);
        tienda.setUpdatedBy(actor);
        return tiendaRepository.save(tienda);
    }

    /**
     * Borra el archivo físico del logo anterior, si lo había. Un fallo al borrar (por
     * ejemplo, el archivo ya no existe o hay un problema de permisos) solo se registra
     * como advertencia: no debe impedir que se guarde el logo nuevo.
     *
     * @param logoPath ruta pública del logo anterior ({@code /uploads/...}), o null/vacío
     *                 si no había
     */
    private void deleteExistingLogoFile(String logoPath) {
        if (logoPath == null || logoPath.isBlank()) return;
        try {
            Path previous = Paths.get(uploadsDir, logoPath.replaceFirst("^/uploads/", ""));
            Files.deleteIfExists(previous);
        } catch (IOException e) {
            log.warn("No se pudo borrar el logo anterior ({}): {}", logoPath, e.getMessage());
        }
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
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
