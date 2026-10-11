package com.nexora.restopos.service;

import com.nexora.restopos.dto.ZonaRequest;
import com.nexora.restopos.model.Zona;
import com.nexora.restopos.model.User;
import com.nexora.restopos.repository.MesaRepository;
import com.nexora.restopos.repository.ZonaRepository;
import com.nexora.restopos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * CRUD de zonas (ej. "Zona principal", "Terraza"), con aislamiento por tienda
 * (multi-tenancy). Sin límite de cantidad: el ADMIN crea las que necesite.
 *
 * <p>Cada zona pertenece a una sola tienda. Las consultas se filtran mediante
 * {@link TenantScope} para que un usuario normal solo vea/edite las zonas de su propia
 * tienda, mientras que un SUPER_ADMIN (sin tienda asignada) puede ver todas.</p>
 */
@Service
@RequiredArgsConstructor
public class ZonaService {

    private final ZonaRepository zonaRepository;
    private final MesaRepository mesaRepository;
    private final TenantScope tenantScope;

    public List<Zona> findAll(User actor) {
        return zonaRepository.findAllForTienda(tenantScope.scopeId(actor));
    }

    public Zona findById(Long id) {
        return zonaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Zona no encontrada: " + id));
    }

    public Zona findById(Long id, User actor) {
        Zona z = findById(id);
        Long scope = tenantScope.scopeId(actor);
        if (scope != null && (z.getTienda() == null || !scope.equals(z.getTienda().getId()))) {
            throw new IllegalArgumentException("Zona no encontrada: " + id);
        }
        return z;
    }

    public Zona create(ZonaRequest req, User actor) {
        Zona z = new Zona();
        z.setNombre(req.getNombre());
        z.setTienda(actor.getTienda());
        z.setCreatedBy(actor);
        return zonaRepository.save(z);
    }

    public Zona update(Long id, ZonaRequest req, User actor) {
        Zona z = findById(id, actor);
        z.setNombre(req.getNombre());
        z.setUpdatedBy(actor);
        return zonaRepository.save(z);
    }

    // Borrado suave (igual que categories/products/mesas) para conservar quién y cuándo la eliminó.
    // Exige que la zona esté vacía: si tuviera mesas, borrarla dejaría esas mesas apuntando
    // a una zona inactiva, invisible en los filtros del tablero.
    public void delete(Long id, User actor) {
        Zona z = findById(id, actor);
        if (mesaRepository.existsByZonaIdAndIsActiveTrue(id)) {
            throw new IllegalStateException("No se puede eliminar una zona con mesas asignadas — reasígnalas primero");
        }
        z.setIsActive(false);
        z.setDeletedBy(actor);
        z.setDeletedAt(LocalDateTime.now());
        zonaRepository.save(z);
    }
}
