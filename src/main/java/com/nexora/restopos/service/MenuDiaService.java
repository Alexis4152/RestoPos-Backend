package com.nexora.restopos.service;

import com.nexora.restopos.dto.MenuDiaDtos;
import com.nexora.restopos.model.MenuDia;
import com.nexora.restopos.model.Product;
import com.nexora.restopos.model.Tienda;
import com.nexora.restopos.model.User;
import com.nexora.restopos.repository.MenuDiaRepository;
import com.nexora.restopos.repository.ProductRepository;
import com.nexora.restopos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Menús con nombre de la tienda y "el menú de hoy" (ver {@link MenuDia}).
 *
 * Regla de venta: si la tienda tiene un menú de hoy, solo sus platillos se pueden ordenar
 * ({@link #enMenuHoy}); lo validan OrdenService (POS, pedido web y pedido de reservación) y
 * filtran PublicSiteService (menú web) y ProductController (buscadores con paraOrdenar=true).
 * Sin menú de hoy se vende todo el catálogo activo, como antes de existir los menús.
 */
@Service
@RequiredArgsConstructor
public class MenuDiaService {

    private final MenuDiaRepository menuRepository;
    private final ProductRepository productRepository;
    private final TenantScope tenantScope;
    private final AuditoriaService auditoriaService;

    @Transactional(readOnly = true)
    public List<MenuDiaDtos.Menu> list(User actor) {
        Long tiendaId = tenantScope.scopeId(actor);
        if (tiendaId == null || tiendaId < 0) return List.of();
        return menuRepository.findByTiendaIdOrderByNombreAsc(tiendaId).stream().map(this::toDto).toList();
    }

    @Transactional
    public MenuDiaDtos.Menu create(MenuDiaDtos.Guardar req, User actor) {
        Tienda tienda = tiendaRequerida(actor);
        String nombre = req.nombre().trim();
        if (menuRepository.existsByTiendaIdAndNombreIgnoreCase(tienda.getId(), nombre)) {
            throw new IllegalArgumentException("Ya tienes un menú llamado \"" + nombre + "\"");
        }
        MenuDia m = new MenuDia();
        m.setNombre(nombre);
        m.setTienda(tienda);
        m.setProductos(productosDeTienda(req.productoIds(), tienda.getId()));
        m.setUpdatedBy(actor);
        // El primer menú de la tienda se vuelve el de hoy: es lo que el usuario espera al crearlo.
        if (!menuRepository.existsMenuHoy(tienda.getId())) m.setHoy(true);
        m = menuRepository.save(m);
        auditoriaService.log(actor, "MENU_CREADO", "MenuDia", m.getId(), null, resumen(m), null);
        return toDto(m);
    }

    @Transactional
    public MenuDiaDtos.Menu update(Long id, MenuDiaDtos.Guardar req, User actor) {
        MenuDia m = find(id, actor);
        String nombre = req.nombre().trim();
        if (menuRepository.existsByTiendaIdAndNombreIgnoreCaseAndIdNot(m.getTienda().getId(), nombre, id)) {
            throw new IllegalArgumentException("Ya tienes un menú llamado \"" + nombre + "\"");
        }
        String antes = resumen(m);
        m.setNombre(nombre);
        m.setProductos(productosDeTienda(req.productoIds(), m.getTienda().getId()));
        m.setUpdatedBy(actor);
        m = menuRepository.save(m);
        auditoriaService.log(actor, "MENU_EDITADO", "MenuDia", m.getId(), antes, resumen(m), null);
        return toDto(m);
    }

    @Transactional
    public void delete(Long id, User actor) {
        MenuDia m = find(id, actor);
        String antes = resumen(m);
        menuRepository.delete(m);
        auditoriaService.log(actor, "MENU_ELIMINADO", "MenuDia", id, antes, null, null);
    }

    /** Lo vuelve el menú de hoy (el que lo era deja de serlo). */
    @Transactional
    public MenuDiaDtos.Menu usarHoy(Long id, User actor) {
        MenuDia m = find(id, actor);
        if (m.getProductos().isEmpty()) {
            throw new IllegalStateException("Agrega al menos un platillo antes de usarlo como menú de hoy");
        }
        menuRepository.quitarHoy(m.getTienda().getId(), m.getId());
        m.setHoy(true);
        m.setUpdatedBy(actor);
        m = menuRepository.save(m);
        auditoriaService.log(actor, "MENU_HOY", "MenuDia", m.getId(), null, m.getNombre(), null);
        return toDto(m);
    }

    /** Sin menú de hoy: se vuelve a vender todo el catálogo activo. */
    @Transactional
    public void quitarHoy(User actor) {
        Tienda tienda = tiendaRequerida(actor);
        if (menuRepository.quitarHoy(tienda.getId(), null) > 0) {
            auditoriaService.log(actor, "MENU_HOY_QUITADO", "MenuDia", null, null, null, "Se vende todo el catálogo");
        }
    }

    /**
     * Ids de los platillos que se pueden ordenar hoy en la tienda, o {@code null} si la tienda
     * no tiene menú de hoy (= sin restricción, todo el catálogo activo).
     */
    @Transactional(readOnly = true)
    public Set<Long> idsMenuHoy(Long tiendaId) {
        if (tiendaId == null || !menuRepository.existsMenuHoy(tiendaId)) return null;
        return menuRepository.findProductoIdsDelMenuHoy(tiendaId);
    }

    /** ¿Se puede ordenar hoy este platillo? (true si su tienda no tiene menú de hoy). */
    @Transactional(readOnly = true)
    public boolean enMenuHoy(Product p) {
        Set<Long> ids = idsMenuHoy(p.getTienda() != null ? p.getTienda().getId() : null);
        return ids == null || ids.contains(p.getId());
    }

    // ── internos ─────────────────────────────────────────────────

    private MenuDia find(Long id, User actor) {
        MenuDia m = menuRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Menú no encontrado"));
        Long scope = tenantScope.scopeId(actor);
        if (scope != null && !scope.equals(m.getTienda().getId())) {
            throw new IllegalArgumentException("Menú no encontrado");
        }
        return m;
    }

    private Tienda tiendaRequerida(User actor) {
        Tienda t = tenantScope.tiendaForWrite(actor);
        if (t == null) throw new IllegalStateException("Elige primero una tienda");
        return t;
    }

    /** Solo platillos activos de esa tienda; los ids ajenos o borrados se ignoran. */
    private Set<Product> productosDeTienda(List<Long> ids, Long tiendaId) {
        if (ids == null || ids.isEmpty()) return new HashSet<>();
        Set<Long> unicos = new LinkedHashSet<>(ids);
        Set<Product> out = new HashSet<>();
        for (Product p : productRepository.findAllById(unicos)) {
            if (p.getTienda() != null && tiendaId.equals(p.getTienda().getId()) && Boolean.TRUE.equals(p.getIsActive())) {
                out.add(p);
            }
        }
        return out;
    }

    private MenuDiaDtos.Menu toDto(MenuDia m) {
        List<Long> ids = new ArrayList<>(m.getProductos().stream().map(Product::getId).toList());
        ids.sort(Long::compareTo);
        return new MenuDiaDtos.Menu(m.getId(), m.getNombre(), Boolean.TRUE.equals(m.getHoy()), ids, m.getUpdatedAt());
    }

    private String resumen(MenuDia m) {
        return m.getNombre() + " · " + m.getProductos().size() + " platillos";
    }
}
