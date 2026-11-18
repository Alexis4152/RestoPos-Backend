package com.nexora.restopos.service;

import com.nexora.restopos.dto.AltaMasivaMesasRequest;
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

    /**
     * Marca como reservables EXACTAMENTE las mesas de {@code mesaIds} (las demás de la tienda
     * quedan no reservables). Se usa desde Página web → Reservaciones, con la opción "solo
     * algunas mesas".
     */
    @org.springframework.transaction.annotation.Transactional
    public void setReservables(java.util.Collection<Long> mesaIds, User actor) {
        java.util.Set<Long> ids = mesaIds == null ? java.util.Set.of() : new java.util.HashSet<>(mesaIds);
        for (Mesa m : findAll(actor)) {
            boolean reservable = ids.contains(m.getId());
            if (!Boolean.valueOf(reservable).equals(m.getReservable())) {
                m.setReservable(reservable);
                m.setUpdatedBy(actor);
                mesaRepository.save(m);
            }
        }
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
        // tiendaForWrite (no actor.getTienda()): un SUPERVISOR/SUPER_ADMIN no tiene tienda
        // propia — con getTienda() la mesa quedaba sin tienda (invisible) al dar de alta una
        // tienda nueva desde plataforma.
        com.nexora.restopos.model.Tienda tienda = tiendaOrError(actor);
        Long tiendaId = tienda.getId();
        if (mesaRepository.existsByNumeroAndTiendaIdAndIsActiveTrue(req.getNumero(), tiendaId)) {
            throw new IllegalArgumentException("Ya existe una mesa con ese número");
        }
        Mesa m = new Mesa();
        m.setNumero(req.getNumero());
        m.setCapacidad(req.getCapacidad());
        m.setZona(resolveZona(req.getZonaId(), actor));
        m.setEstado(MesaEstado.DISPONIBLE);
        m.setReservable(req.getReservable() == null || req.getReservable());
        m.setTienda(tienda);
        m.setCreatedBy(actor);
        return mesaRepository.save(m);
    }

    private com.nexora.restopos.model.Tienda tiendaOrError(User actor) {
        com.nexora.restopos.model.Tienda t = tenantScope.tiendaForWrite(actor);
        if (t == null) throw new IllegalStateException("Elige una tienda para poder dar de alta mesas");
        return t;
    }

    /**
     * Alta de varias mesas y/o zonas de un golpe (ver {@link AltaMasivaMesasRequest}). Todo o
     * nada: valida primero que ningún número choque con una mesa existente ni se repita dentro
     * del mismo pedido, y solo entonces crea zonas y mesas.
     *
     * @return {@code {zonasCreadas, mesasCreadas}}
     * @throws IllegalArgumentException si algún número ya existe o se repite (los lista)
     */
    @org.springframework.transaction.annotation.Transactional
    public java.util.Map<String, Integer> altaMasiva(AltaMasivaMesasRequest req, User actor) {
        com.nexora.restopos.model.Tienda tienda = tiendaOrError(actor);
        Long tiendaId = tienda.getId();
        List<Zona> zonasTienda = zonaRepository.findAllForTienda(tiendaId);

        // 1) Números a crear y validación (antes de tocar nada).
        java.util.Set<String> existentes = new java.util.HashSet<>();
        mesaRepository.findAllForTienda(tiendaId).forEach(m -> existentes.add(m.getNumero().trim().toLowerCase()));
        java.util.Set<String> nuevos = new java.util.HashSet<>();
        java.util.List<String> chocan = new java.util.ArrayList<>();
        java.util.List<java.util.List<String>> numerosPorGrupo = new java.util.ArrayList<>();
        for (AltaMasivaMesasRequest.Grupo g : req.getGrupos()) {
            if (g.getZonaId() == null && (g.getZonaNombre() == null || g.getZonaNombre().isBlank())) {
                throw new IllegalArgumentException("Cada grupo necesita una zona: elige una o escribe el nombre de la nueva");
            }
            String prefijo = g.getPrefijo() == null ? "" : g.getPrefijo().trim();
            java.util.List<String> numeros = new java.util.ArrayList<>();
            for (int i = 0; i < g.getCantidad(); i++) {
                String numero = prefijo + (g.getDesde() + i);
                if (numero.length() > 20) throw new IllegalArgumentException("El número de mesa " + numero + " es demasiado largo");
                String clave = numero.toLowerCase();
                if (existentes.contains(clave) || !nuevos.add(clave)) chocan.add(numero);
                numeros.add(numero);
            }
            numerosPorGrupo.add(numeros);
        }
        if (!chocan.isEmpty()) {
            String lista = chocan.stream().limit(15).collect(java.util.stream.Collectors.joining(", "));
            throw new IllegalArgumentException("Ya existen (o se repiten) las mesas: " + lista
                    + (chocan.size() > 15 ? " y " + (chocan.size() - 15) + " más" : "")
                    + ". Cambia el número inicial o el prefijo.");
        }

        // 2) Zonas (existentes por id, existentes por nombre, o nuevas) y mesas.
        int zonasCreadas = 0;
        int mesasCreadas = 0;
        java.util.Map<String, Zona> nuevasPorNombre = new java.util.HashMap<>();
        for (int gi = 0; gi < req.getGrupos().size(); gi++) {
            AltaMasivaMesasRequest.Grupo g = req.getGrupos().get(gi);
            Zona zona;
            if (g.getZonaId() != null) {
                zona = resolveZona(g.getZonaId(), actor);
            } else {
                String nombre = g.getZonaNombre().trim();
                String clave = nombre.toLowerCase();
                zona = zonasTienda.stream().filter(z -> z.getNombre() != null && z.getNombre().trim().equalsIgnoreCase(nombre))
                        .findFirst().orElse(nuevasPorNombre.get(clave));
                if (zona == null) {
                    Zona z = new Zona();
                    z.setNombre(nombre);
                    z.setTienda(tienda);
                    z.setCreatedBy(actor);
                    zona = zonaRepository.save(z);
                    nuevasPorNombre.put(clave, zona);
                    zonasCreadas++;
                }
            }
            for (String numero : numerosPorGrupo.get(gi)) {
                Mesa m = new Mesa();
                m.setNumero(numero);
                m.setCapacidad(g.getCapacidad());
                m.setZona(zona);
                m.setEstado(MesaEstado.DISPONIBLE);
                m.setReservable(true);
                m.setTienda(tienda);
                m.setCreatedBy(actor);
                mesaRepository.save(m);
                mesasCreadas++;
            }
        }
        return java.util.Map.of("zonasCreadas", zonasCreadas, "mesasCreadas", mesasCreadas);
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

    /**
     * Desactiva varias mesas de un golpe. Todo o nada: si alguna tiene una orden abierta (o no
     * es de la tienda), no se desactiva ninguna y se dice cuáles impiden hacerlo.
     *
     * @return cuántas mesas se desactivaron
     * @throws IllegalStateException si alguna tiene orden abierta (las lista)
     */
    @org.springframework.transaction.annotation.Transactional
    public int deactivateMany(java.util.Collection<Long> ids, User actor) {
        if (ids == null || ids.isEmpty()) throw new IllegalArgumentException("Elige al menos una mesa");
        if (ids.size() > 500) throw new IllegalArgumentException("Máximo 500 mesas a la vez");
        List<Mesa> mesas = ids.stream().distinct().map(id -> findById(id, actor)).toList();
        List<String> ocupadas = mesas.stream().filter(m -> m.getOrdenActiva() != null).map(Mesa::getNumero).toList();
        if (!ocupadas.isEmpty()) {
            throw new IllegalStateException("No se pueden desactivar mesas con una orden abierta: "
                    + String.join(", ", ocupadas) + ". Ciérralas o quítalas de la selección.");
        }
        LocalDateTime ahora = LocalDateTime.now();
        for (Mesa m : mesas) {
            m.setIsActive(false);
            m.setDeletedBy(actor);
            m.setDeletedAt(ahora);
            mesaRepository.save(m);
        }
        return mesas.size();
    }
}
