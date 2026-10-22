package com.nexora.restopos.service;

import com.nexora.restopos.dto.MesaRequest;
import com.nexora.restopos.model.Mesa;
import com.nexora.restopos.model.MesaEstado;
import com.nexora.restopos.model.User;
import com.nexora.restopos.model.Zona;
import com.nexora.restopos.repository.MesaRepository;
import com.nexora.restopos.repository.ZonaRepository;
import com.nexora.restopos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * CRUD de mesas y transiciones de su {@link MesaEstado}, con aislamiento por tienda.
 * Las transiciones que dependen de una {@link Orden} activa (ocupar, liberar, marcar
 * pendiente de pago) las dispara {@link OrdenService} y {@link CuentaService}, no este
 * service: aquí solo vive el CRUD y los cambios manuales de estado del ADMIN.
 */
@Service
@RequiredArgsConstructor
public class MesaService {

    private final MesaRepository mesaRepository;
    private final ZonaRepository zonaRepository;
    private final TenantScope tenantScope;

    public List<Mesa> findAll(User actor) {
        return mesaRepository.findAllForTienda(tenantScope.scopeId(actor));
    }

    public Mesa findById(Long id) {
        return mesaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Mesa no encontrada: " + id));
    }

    public Mesa findById(Long id, User actor) {
        Mesa m = findById(id);
        Long scope = tenantScope.scopeId(actor);
        if (scope != null && (m.getTienda() == null || !scope.equals(m.getTienda().getId()))) {
            throw new IllegalArgumentException("Mesa no encontrada: " + id);
        }
        return m;
    }

    public Mesa create(MesaRequest req, User actor) {
        Long tiendaId = actor.getTienda() != null ? actor.getTienda().getId() : null;
        if (mesaRepository.existsByNumeroAndTiendaIdAndIsActiveTrue(req.getNumero(), tiendaId)) {
            throw new IllegalArgumentException("Ya existe una mesa con ese número");
        }
        Mesa m = new Mesa();
        m.setNumero(req.getNumero());
        m.setCapacidad(req.getCapacidad());
        m.setZona(resolveZona(req.getZonaId(), actor));
        m.setEstado(MesaEstado.DISPONIBLE);
        m.setReservable(req.getReservable() == null || req.getReservable());
        m.setTienda(actor.getTienda());
        m.setCreatedBy(actor);
        return mesaRepository.save(m);
    }

    public Mesa update(Long id, MesaRequest req, User actor) {
        Mesa m = findById(id, actor);
        m.setNumero(req.getNumero());
        m.setCapacidad(req.getCapacidad());
        if (req.getReservable() != null) m.setReservable(req.getReservable());
        m.setZona(resolveZona(req.getZonaId(), actor));
        // el estado solo se puede forzar a mano si no hay una orden activa gobernándolo
        if (req.getEstado() != null) {
            if (m.getOrdenActiva() != null) {
                throw new IllegalStateException("No se puede cambiar el estado de una mesa con una orden activa");
            }
            m.setEstado(req.getEstado());
        }
        m.setUpdatedBy(actor);
        return mesaRepository.save(m);
    }

    /** Resuelve la zona indicada, validando que pertenezca a la misma tienda que la mesa. */
    private Zona resolveZona(Long zonaId, User actor) {
        Zona zona = zonaRepository.findById(zonaId)
                .orElseThrow(() -> new IllegalArgumentException("Zona no encontrada: " + zonaId));
        Long scope = tenantScope.scopeId(actor);
        if (scope != null && (zona.getTienda() == null || !scope.equals(zona.getTienda().getId()))) {
            throw new IllegalArgumentException("Zona no encontrada: " + zonaId);
        }
        return zona;
    }

    public void deactivate(Long id, User actor) {
        Mesa m = findById(id, actor);
        if (m.getOrdenActiva() != null) {
            throw new IllegalStateException("No se puede desactivar una mesa con una orden activa");
        }
        m.setIsActive(false);
        m.setDeletedBy(actor);
        m.setDeletedAt(LocalDateTime.now());
        mesaRepository.save(m);
    }
}
