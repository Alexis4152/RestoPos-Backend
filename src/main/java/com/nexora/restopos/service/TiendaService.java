package com.nexora.restopos.service;

import com.nexora.restopos.dto.TiendaRequest;
import com.nexora.restopos.model.Tienda;
import com.nexora.restopos.model.User;
import com.nexora.restopos.repository.TiendaRepository;
import com.nexora.restopos.repository.UserRepository;
import com.nexora.restopos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * CRUD de tiendas (sucursales/negocios) y gestión de su color de marca (theming).
 *
 * <p>Una {@link Tienda} es la unidad raíz del multi-tenancy: todo (productos, ventas,
 * cortes de caja, roles, usuarios) cuelga de una tienda. Este servicio solo administra
 * la tienda en sí (alta, nombre, color, baja); los datos fiscales/de contacto viven en
 * {@link TiendaInfoService} y el logo en {@link TiendaLogoService}.</p>
 */
@Service
@RequiredArgsConstructor
public class TiendaService {

    private final TiendaRepository tiendaRepository;
    private final UserRepository userRepository;
    private final RoleService roleService;
    private final TenantScope tenantScope;

    /**
     * Lista las tiendas visibles para el actor: un SUPERVISOR solo ve las que tiene a su
     * cargo; un SUPER_ADMIN ve todas (o solo las de un supervisor específico, si se pasa
     * {@code supervisorId} — por ejemplo, para revisar la cartera de un supervisor).
     *
     * @param actor usuario que realiza la consulta
     * @param supervisorId filtro opcional por supervisor a cargo (solo tiene efecto para SUPER_ADMIN)
     * @return tiendas dentro del alcance del actor, ordenadas por nombre
     */
    public List<Tienda> findAll(User actor, Long supervisorId) {
        if (tenantScope.isSupervisor(actor)) {
            return tiendaRepository.findBySupervisorIdOrderByNameAsc(actor.getId());
        }
        if (supervisorId != null) {
            return tiendaRepository.findBySupervisorIdOrderByNameAsc(supervisorId);
        }
        return tiendaRepository.findAllByOrderByNameAsc();
    }

    /**
     * Busca una tienda por id.
     *
     * @param id id de la tienda
     * @return la tienda encontrada
     * @throws IllegalArgumentException si no existe
     */
    public Tienda findById(Long id) {
        return tiendaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tienda no encontrada: " + id));
    }

    /**
     * Da de alta una tienda nueva y le siembra sus roles por defecto (ADMIN, CASHIER,
     * SELLER) vía {@link RoleService#seedDefaultRolesForTienda(Tienda)}, para que quede
     * lista para operar (dar de alta usuarios, productos, etc.) de inmediato.
     *
     * @param req datos de la tienda (nombre)
     * @param actor usuario que la crea (normalmente SUPER_ADMIN); queda registrado como
     *              {@code createdBy}
     * @return la tienda creada, ya con sus roles base sembrados
     */
    public Tienda create(TiendaRequest req, User actor) {
        Tienda t = new Tienda();
        t.setName(req.getName());
        t.setIsActive(true);
        t.setCreatedBy(actor);
        if (tenantScope.isSupervisor(actor)) {
            // el SUPERVISOR que la crea queda automáticamente a cargo de ella
            t.setSupervisor(actor);
        } else if (tenantScope.isSuperAdmin(actor) && req.getSupervisorId() != null) {
            t.setSupervisor(resolveSupervisor(req.getSupervisorId()));
        }
        Tienda saved = tiendaRepository.save(t);
        roleService.seedDefaultRolesForTienda(saved);
        return saved;
    }

    /**
     * Actualiza el nombre (y, si el actor es SUPER_ADMIN, el supervisor a cargo) de una tienda.
     *
     * @param id id de la tienda
     * @param req nuevos datos (nombre, supervisor opcional)
     * @param actor usuario que hace el cambio; queda registrado como {@code updatedBy}
     * @return la tienda actualizada
     */
    public Tienda update(Long id, TiendaRequest req, User actor) {
        Tienda t = findById(id);
        t.setName(req.getName());
        if (tenantScope.isSuperAdmin(actor) && req.getSupervisorId() != null) {
            t.setSupervisor(resolveSupervisor(req.getSupervisorId()));
        }
        t.setUpdatedBy(actor);
        return tiendaRepository.save(t);
    }

    // Valida que el id dado corresponda de verdad a un usuario con rol SUPERVISOR antes de
    // asignarlo como responsable de una tienda.
    private User resolveSupervisor(Long supervisorId) {
        User u = userRepository.findById(supervisorId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + supervisorId));
        if (!tenantScope.isSupervisor(u)) {
            throw new IllegalArgumentException("El usuario indicado no tiene rol SUPERVISOR");
        }
        return u;
    }

    /**
     * Da de baja (borrado suave) una tienda: marca {@code isActive=false} y registra
     * quién y cuándo, sin borrar la fila ni sus datos relacionados.
     *
     * @param id id de la tienda a desactivar
     * @param actor usuario que la desactiva
     */
    public void deactivate(Long id, User actor) {
        Tienda t = findById(id);
        t.setIsActive(false);
        t.setDeletedBy(actor);
        t.setDeletedAt(java.time.LocalDateTime.now());
        tiendaRepository.save(t);
    }

    // El color de marca lo puede cambiar el SUPER_ADMIN (cualquier tienda) o el ADMIN
    // de esa misma tienda — nunca el ADMIN de otra tienda.
    /**
     * Actualiza el color primario de marca de una tienda, usado para personalizar la UI
     * del frontend con la identidad de cada negocio.
     *
     * @param id id de la tienda
     * @param primaryColor nuevo color primario (formato esperado por el frontend, p. ej. hex)
     * @param actor usuario que hace el cambio; debe poder administrar esa tienda; queda
     *              registrado como {@code updatedBy}
     * @return la tienda con el color actualizado
     * @throws org.springframework.security.access.AccessDeniedException si el actor no
     *         tiene permiso sobre esa tienda
     */
    public Tienda updateTheme(Long id, String primaryColor, User actor) {
        Tienda t = findById(id);
        if (!tenantScope.canManageTienda(actor, id)) {
            throw new AccessDeniedException("No tienes permiso para modificar el color de esta tienda");
        }
        t.setPrimaryColor(primaryColor);
        t.setUpdatedBy(actor);
        return tiendaRepository.save(t);
    }
}
