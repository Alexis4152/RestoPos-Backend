package com.nexora.restopos.security;

import com.nexora.restopos.model.Tienda;
import com.nexora.restopos.model.User;
import com.nexora.restopos.repository.TiendaRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Helper central de aislamiento multi-tienda ("Tienda"), usado por prácticamente todos los
 * services de negocio para decidir qué datos puede ver o modificar el usuario que hace la
 * petición ({@code actor}).
 * <p>
 * Hay dos roles de plataforma sin tienda propia: {@code SUPER_ADMIN} (ve/administra todas las
 * tiendas) y {@code SUPERVISOR} (ve/administra solo las tiendas que tiene asignadas, vía
 * {@link Tienda#getSupervisor()}). Ninguno de los dos "opera" sobre una tienda por default: el
 * frontend les hace elegir una (ver {@code /select-tienda}) y, a partir de ahí, cada petición
 * manda el header {@link #ACTING_TIENDA_HEADER} indicando sobre cuál están actuando — así el
 * resto del sistema (roles, cortes de caja, órdenes, etc.) los trata igual que al ADMIN de esa
 * tienda para efectos de lectura/escritura, sin que el actor dueño de esos módulos tenga que
 * saber que quien lo está usando es en realidad un actor de plataforma.
 */
@Component
@RequiredArgsConstructor
public class TenantScope {

    /** Header que un SUPER_ADMIN/SUPERVISOR manda para indicar sobre qué tienda está actuando. */
    public static final String ACTING_TIENDA_HEADER = "X-Acting-Tienda-Id";

    private final TiendaRepository tiendaRepository;

    /**
     * @param actor usuario que realiza la operación
     * @return {@code true} si su rol es {@code SUPER_ADMIN} (usuario de plataforma, sin
     *         tienda propia, con visibilidad total)
     */
    public boolean isSuperAdmin(User actor) {
        return "SUPER_ADMIN".equals(actor.getRole().getName());
    }

    /**
     * @param actor usuario que realiza la operación
     * @return {@code true} si su rol es {@code SUPERVISOR} (usuario de plataforma, sin tienda
     *         propia, a cargo de un subconjunto de tiendas)
     */
    public boolean isSupervisor(User actor) {
        return "SUPERVISOR".equals(actor.getRole().getName());
    }

    /** @return {@code true} si el actor es un usuario de plataforma (SUPER_ADMIN o SUPERVISOR), sin tienda propia. */
    public boolean isPlatformActor(User actor) {
        return isSuperAdmin(actor) || isSupervisor(actor);
    }

    /**
     * @return {@code true} si el actor opera con privilegios de administrador de tienda: un
     *         ADMIN "de verdad", o un actor de plataforma (que, una vez elige tienda, se
     *         comporta como el ADMIN de esa tienda).
     */
    public boolean isAdminLevel(User actor) {
        return "ADMIN".equals(actor.getRole().getName()) || isPlatformActor(actor);
    }

    /** Lee el header {@link #ACTING_TIENDA_HEADER} de la petición HTTP actual, si existe. */
    private Long actingTiendaHeaderId() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return null;
        HttpServletRequest request = attrs.getRequest();
        String header = request.getHeader(ACTING_TIENDA_HEADER);
        if (header == null || header.isBlank()) return null;
        try {
            return Long.valueOf(header.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Nunca confía a ciegas en el header: valida que la tienda exista y que, si el actor es
    // SUPERVISOR, de verdad esté a su cargo. Así un SUPERVISOR jamás puede "actuar" sobre una
    // tienda ajena solo mandando otro id en el header.
    private Tienda verifiedActingTienda(User actor) {
        Long actingId = actingTiendaHeaderId();
        if (actingId == null) return null;
        Tienda tienda = tiendaRepository.findById(actingId).orElse(null);
        if (tienda == null) return null;
        if (isSuperAdmin(actor)) return tienda;
        if (isSupervisor(actor)) {
            return (tienda.getSupervisor() != null && tienda.getSupervisor().getId().equals(actor.getId()))
                    ? tienda : null;
        }
        return null;
    }

    /**
     * Calcula el id de tienda por el que se debe filtrar una consulta para este actor.
     *
     * @param actor usuario que realiza la operación
     * @return para SUPER_ADMIN: {@code null} (sin filtro, ve todas) salvo que haya elegido
     *         actuar sobre una tienda específica; para SUPERVISOR: el id de la tienda elegida
     *         (si de verdad está a su cargo) o {@code -1L} si no ha elegido ninguna — nunca
     *         {@code null}, para que jamás vea "todas las tiendas" sin querer; para el resto,
     *         el id de su propia tienda.
     */
    public Long scopeId(User actor) {
        if (isSuperAdmin(actor)) {
            Tienda acting = verifiedActingTienda(actor);
            return acting != null ? acting.getId() : null;
        }
        if (isSupervisor(actor)) {
            Tienda acting = verifiedActingTienda(actor);
            return acting != null ? acting.getId() : -1L;
        }
        return actor.getTienda() != null ? actor.getTienda().getId() : null;
    }

    /**
     * Resuelve la tienda sobre la que el actor debe escribir datos nuevos (roles, cortes de
     * caja, órdenes, etc.): para un actor de plataforma, la tienda que eligió actuar (o
     * {@code null} si no ha elegido ninguna); para cualquier otro usuario, siempre la suya.
     *
     * @param actor usuario que realiza la operación
     * @return la tienda a usar al crear un registro nuevo, o {@code null} si no se puede
     *         determinar (actor de plataforma sin tienda elegida)
     */
    public Tienda tiendaForWrite(User actor) {
        if (isPlatformActor(actor)) return verifiedActingTienda(actor);
        return actor.getTienda();
    }

    /**
     * Determina si el actor tiene permiso de escritura sobre una tienda específica (por
     * ejemplo, para editar su personalización: color de marca, logo, datos fiscales).
     *
     * @param actor    usuario que realiza la operación
     * @param tiendaId id de la tienda que se quiere administrar
     * @return {@code true} si es SUPER_ADMIN (puede administrar cualquier tienda), si es
     *         SUPERVISOR y esa tienda está a su cargo, o si el actor pertenece exactamente a
     *         esa tienda; {@code false} en cualquier otro caso
     */
    public boolean canManageTienda(User actor, Long tiendaId) {
        if (isSuperAdmin(actor)) return true;
        if (isSupervisor(actor)) {
            return tiendaRepository.findById(tiendaId)
                    .map(t -> t.getSupervisor() != null && t.getSupervisor().getId().equals(actor.getId()))
                    .orElse(false);
        }
        return actor.getTienda() != null && actor.getTienda().getId().equals(tiendaId);
    }
}
