package com.nexora.restopos.service;

import com.nexora.restopos.dto.ExtrasVentaDtos;
import com.nexora.restopos.dto.ModificadorGrupoCopiableView;
import com.nexora.restopos.dto.ModificadorGrupoRequest;
import com.nexora.restopos.dto.ModificadorOpcionRequest;
import com.nexora.restopos.model.ModificadorGrupo;
import com.nexora.restopos.model.ModificadorOpcion;
import com.nexora.restopos.model.Product;
import com.nexora.restopos.model.User;
import com.nexora.restopos.repository.ModificadorGrupoRepository;
import com.nexora.restopos.repository.ModificadorOpcionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CRUD de {@link ModificadorGrupo} (y sus {@link ModificadorOpcion} anidadas) de un producto —
 * ej. "Término de cocción", "Extras". Un grupo y sus opciones se guardan como una sola unidad
 * (ver {@link ModificadorGrupoRequest}): no hay endpoints separados por opción.
 *
 * <p>{@link ProductService#findById(Long, User)} valida el acceso al producto (tienda del actor)
 * en cada operación, así que este servicio no repite esa lógica de tenant scope.</p>
 */
@Service
@RequiredArgsConstructor
public class ModificadorGrupoService {

    private final ModificadorGrupoRepository modificadorGrupoRepository;
    private final ModificadorOpcionRepository modificadorOpcionRepository;
    private final ProductService productService;
    private final AuditoriaService auditoriaService;

    public List<ModificadorGrupo> findAllByProducto(Long productoId, User actor) {
        productService.findById(productoId, actor);
        return modificadorGrupoRepository.findAllByProductoIdAndIsActiveTrueOrderByOrdenAscIdAsc(productoId);
    }

    /**
     * Grupos de OTROS productos de la misma categoría que {@code productoId}, para poder
     * copiarlos a este producto en vez de recapturar desde cero uno ya armado para un platillo
     * similar (ej. "Término de cocción" ya creado para otro corte). Copiar, no compartir: el
     * grupo resultante queda completamente independiente del original (ver {@link #create}) —
     * editar uno no afecta al otro.
     */
    public List<ModificadorGrupoCopiableView> findCopiablesDeLaMismaCategoria(Long productoId, User actor) {
        Product producto = productService.findById(productoId, actor);
        if (producto.getCategory() == null) return List.of();
        List<ModificadorGrupo> grupos = modificadorGrupoRepository
                .findAllByProducto_Category_IdAndIsActiveTrueAndProducto_IdNotOrderByNombreAsc(
                        producto.getCategory().getId(), productoId);
        return grupos.stream()
                .map(g -> ModificadorGrupoCopiableView.builder()
                        .id(g.getId())
                        .nombre(g.getNombre())
                        .tipo(g.getTipo())
                        .obligatorio(g.getObligatorio())
                        .maxSelecciones(g.getMaxSelecciones())
                        .permiteRepetir(g.getPermiteRepetir())
                        .esExtras(g.getEsExtras())
                        .productoNombre(g.getProducto().getName())
                        .opciones(g.getOpciones().stream().filter(o -> Boolean.TRUE.equals(o.getIsActive())).toList())
                        .build())
                .toList();
    }

    /**
     * Máximo de opciones del grupo: solo aplica a MULTIPLE (en UNICO ya es 1); null = sin límite.
     * Puede ser mayor que el número de opciones: sin repetir, eso equivale a poder marcarlas todas;
     * repitiendo, cuenta piezas (ver {@code permiteRepetir}).
     */
    private static Integer maxSelecciones(ModificadorGrupoRequest req) {
        if (req.getTipo() != com.nexora.restopos.model.ModificadorGrupoTipo.MULTIPLE) return null;
        return req.getMaxSelecciones();
    }

    /** Repetir la misma opción solo aplica a grupos de varias opciones. */
    private static boolean permiteRepetir(ModificadorGrupoRequest req) {
        return req.getTipo() == com.nexora.restopos.model.ModificadorGrupoTipo.MULTIPLE && Boolean.TRUE.equals(req.getPermiteRepetir());
    }

    public ModificadorGrupo findById(Long id, User actor) {
        ModificadorGrupo grupo = modificadorGrupoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Grupo de modificadores no encontrado: " + id));
        productService.findById(grupo.getProducto().getId(), actor); // valida acceso/tenant
        return grupo;
    }

    @Transactional
    public ModificadorGrupo create(Long productoId, ModificadorGrupoRequest req, User actor) {
        Product producto = productService.findById(productoId, actor);
        validateNombreUnicoEnProducto(productoId, req.getNombre(), null);
        validateOpcionesSinNombresRepetidos(req.getOpciones());

        ModificadorGrupo grupo = new ModificadorGrupo();
        grupo.setProducto(producto);
        grupo.setNombre(req.getNombre());
        grupo.setTipo(req.getTipo());
        grupo.setObligatorio(Boolean.TRUE.equals(req.getObligatorio()));
        grupo.setMaxSelecciones(maxSelecciones(req));
        grupo.setPermiteRepetir(permiteRepetir(req));
        grupo.setEsExtras(Boolean.TRUE.equals(req.getEsExtras()));
        // Los grupos nuevos van al final de los que ya tiene el platillo.
        grupo.setOrden(modificadorGrupoRepository.findAllByProductoIdAndIsActiveTrueOrderByOrdenAscIdAsc(productoId).stream()
                .mapToInt(g -> g.getOrden() == null ? 0 : g.getOrden()).max().orElse(-1) + 1);
        grupo.setCreatedBy(actor);
        ModificadorGrupo saved = modificadorGrupoRepository.save(grupo);

        int orden = 0;
        for (ModificadorOpcionRequest or : req.getOpciones()) {
            ModificadorOpcion opcion = new ModificadorOpcion();
            opcion.setGrupo(saved);
            opcion.setNombre(or.getNombre());
            opcion.setPrecioExtra(or.getPrecioExtra());
            opcion.setOrden(orden++);
            modificadorOpcionRepository.save(opcion);
        }
        return findById(saved.getId(), actor);
    }

    /**
     * Actualiza nombre/tipo/obligatorio del grupo y reconcilia sus opciones contra la lista
     * enviada: las que traen {@code id} se actualizan, las que no traen se crean, y las
     * existentes que ya no vienen en la lista se desactivan — nunca se borran físicamente,
     * porque las órdenes que ya las usaron guardan su propia copia (ver
     * {@link com.nexora.restopos.model.OrdenDetalleModificador}).
     */
    @Transactional
    public ModificadorGrupo update(Long id, ModificadorGrupoRequest req, User actor) {
        ModificadorGrupo grupo = findById(id, actor);
        validateNombreUnicoEnProducto(grupo.getProducto().getId(), req.getNombre(), id);
        validateOpcionesSinNombresRepetidos(req.getOpciones());
        grupo.setNombre(req.getNombre());
        grupo.setTipo(req.getTipo());
        grupo.setObligatorio(Boolean.TRUE.equals(req.getObligatorio()));
        grupo.setMaxSelecciones(maxSelecciones(req));
        grupo.setPermiteRepetir(permiteRepetir(req));
        grupo.setEsExtras(Boolean.TRUE.equals(req.getEsExtras()));
        grupo.setUpdatedBy(actor);
        modificadorGrupoRepository.save(grupo);

        Set<Long> idsEnviados = req.getOpciones().stream()
                .map(ModificadorOpcionRequest::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        for (ModificadorOpcion existente : modificadorOpcionRepository.findAllByGrupoIdAndIsActiveTrue(id)) {
            if (!idsEnviados.contains(existente.getId())) {
                existente.setIsActive(false);
                modificadorOpcionRepository.save(existente);
            }
        }

        int orden = 0;
        for (ModificadorOpcionRequest or : req.getOpciones()) {
            ModificadorOpcion opcion;
            if (or.getId() != null) {
                opcion = modificadorOpcionRepository.findById(or.getId())
                        .filter(o -> o.getGrupo().getId().equals(id))
                        .orElseThrow(() -> new IllegalArgumentException("Opción no encontrada: " + or.getId()));
            } else {
                opcion = new ModificadorOpcion();
                opcion.setGrupo(grupo);
            }
            opcion.setNombre(or.getNombre());
            opcion.setPrecioExtra(or.getPrecioExtra());
            opcion.setOrden(orden++);
            modificadorOpcionRepository.save(opcion);
        }
        return findById(id, actor);
    }

    /**
     * Valida que ninguna opción del grupo repita nombre contra otra del mismo grupo
     * (insensible a mayúsculas/espacios) — dos opciones idénticas en el mismo grupo (ej. dos
     * "Extra queso" en "Extras") confundirían al mesero al elegir y no aportan nada distinto.
     *
     * @throws IllegalArgumentException si hay dos o más opciones con el mismo nombre
     */
    private void validateOpcionesSinNombresRepetidos(List<ModificadorOpcionRequest> opciones) {
        Set<String> vistos = new java.util.HashSet<>();
        for (ModificadorOpcionRequest o : opciones) {
            String clave = o.getNombre().trim().toLowerCase();
            if (!vistos.add(clave)) {
                throw new IllegalArgumentException("Ya hay una opción llamada \"" + o.getNombre().trim() + "\" en este grupo");
            }
        }
    }

    /**
     * Valida que el nombre del grupo no choque con el de otro grupo ACTIVO del mismo producto
     * (insensible a mayúsculas/espacios) — ej. no tiene sentido tener dos grupos "Extras" en el
     * mismo platillo, el mesero no sabría cuál de los dos está eligiendo.
     *
     * @param productoId   id del producto dueño de los grupos
     * @param nombre       nombre propuesto para el grupo
     * @param excludeGrupoId id del propio grupo a excluir (al editar, para no chocar contra sí
     *                       mismo); {@code null} al crear
     * @throws IllegalArgumentException si otro grupo activo del producto ya usa ese nombre
     */
    private void validateNombreUnicoEnProducto(Long productoId, String nombre, Long excludeGrupoId) {
        boolean choca = modificadorGrupoRepository.findAllByProductoIdAndIsActiveTrueOrderByOrdenAscIdAsc(productoId).stream()
                .anyMatch(g -> !g.getId().equals(excludeGrupoId) && g.getNombre().trim().equalsIgnoreCase(nombre.trim()));
        if (choca) {
            throw new IllegalArgumentException("Ya existe un grupo llamado \"" + nombre.trim() + "\" en este platillo");
        }
    }

    /**
     * Reordena los grupos activos de un producto según {@code grupoIds} (el primero queda
     * arriba). Debe traer exactamente los grupos activos del producto, ni más ni menos — así un
     * listado viejo en otra pestaña no deja el orden a medias.
     */
    @Transactional
    public List<ModificadorGrupo> reordenar(Long productoId, List<Long> grupoIds, User actor) {
        productService.findById(productoId, actor);
        List<ModificadorGrupo> grupos = modificadorGrupoRepository.findAllByProductoIdAndIsActiveTrueOrderByOrdenAscIdAsc(productoId);
        Set<Long> actuales = grupos.stream().map(ModificadorGrupo::getId).collect(Collectors.toSet());
        if (grupoIds.contains(null) || grupoIds.size() != actuales.size() || !actuales.equals(Set.copyOf(grupoIds))) {
            throw new IllegalArgumentException("Los grupos cambiaron mientras ordenabas; recarga e inténtalo de nuevo");
        }
        java.util.Map<Long, ModificadorGrupo> porId = grupos.stream()
                .collect(Collectors.toMap(ModificadorGrupo::getId, g -> g));
        for (int i = 0; i < grupoIds.size(); i++) {
            ModificadorGrupo g = porId.get(grupoIds.get(i));
            g.setOrden(i);
            g.setUpdatedBy(actor);
        }
        modificadorGrupoRepository.saveAll(grupos);
        return modificadorGrupoRepository.findAllByProductoIdAndIsActiveTrueOrderByOrdenAscIdAsc(productoId);
    }

    /**
     * Desde la toma de orden: cambia el precio de una opción de un grupo de Extras. Se guarda en
     * el platillo sin confirmación; lo que se pida de aquí en adelante toma el precio nuevo.
     */
    @Transactional
    public ModificadorOpcion cambiarPrecioExtra(Long opcionId, ExtrasVentaDtos.Precio req, User actor) {
        ModificadorOpcion opcion = modificadorOpcionRepository.findById(opcionId)
                .filter(o -> Boolean.TRUE.equals(o.getIsActive()))
                .orElseThrow(() -> new IllegalArgumentException("Ese extra ya no existe"));
        ModificadorGrupo grupo = grupoDeExtras(opcion.getGrupo().getId(), actor);
        java.math.BigDecimal nuevo = req.precioExtra().setScale(2, java.math.RoundingMode.HALF_UP);
        java.math.BigDecimal anterior = opcion.getPrecioExtra();
        if (anterior != null && anterior.compareTo(nuevo) == 0) return opcion;
        opcion.setPrecioExtra(nuevo);
        ModificadorOpcion saved = modificadorOpcionRepository.save(opcion);
        auditoriaService.log(actor, "PRECIO_EXTRA", "ModificadorOpcion", saved.getId(), anterior, nuevo,
                grupo.getProducto().getName() + " · " + grupo.getNombre() + " · " + saved.getNombre());
        return saved;
    }

    /**
     * Desde la toma de orden: agrega un extra (nombre y precio) a un grupo de Extras del platillo.
     * Si ya había uno con ese nombre que se quitó, se reactiva con el precio nuevo.
     */
    @Transactional
    public ModificadorOpcion agregarExtra(Long grupoId, ExtrasVentaDtos.Nuevo req, User actor) {
        ModificadorGrupo grupo = grupoDeExtras(grupoId, actor);
        String nombre = req.nombre().trim();
        if (nombre.isEmpty()) throw new IllegalArgumentException("Escribe el nombre del extra");
        java.math.BigDecimal precio = req.precioExtra().setScale(2, java.math.RoundingMode.HALF_UP);
        ModificadorOpcion existente = grupo.getOpciones().stream()
                .filter(o -> o.getNombre().trim().equalsIgnoreCase(nombre))
                .findFirst().orElse(null);
        if (existente != null && Boolean.TRUE.equals(existente.getIsActive())) {
            throw new IllegalArgumentException("\"" + nombre + "\" ya está en " + grupo.getNombre());
        }
        ModificadorOpcion opcion = existente != null ? existente : new ModificadorOpcion();
        opcion.setGrupo(grupo);
        opcion.setNombre(nombre);
        opcion.setPrecioExtra(precio);
        opcion.setIsActive(true);
        opcion.setOrden(grupo.getOpciones().stream().mapToInt(o -> o.getOrden() == null ? 0 : o.getOrden()).max().orElse(-1) + 1);
        ModificadorOpcion saved = modificadorOpcionRepository.save(opcion);
        auditoriaService.log(actor, "NUEVO_EXTRA", "ModificadorOpcion", saved.getId(), null, precio,
                grupo.getProducto().getName() + " · " + grupo.getNombre() + " · " + saved.getNombre());
        return saved;
    }

    /** El grupo (validando la tienda del actor), que debe estar activo y ser de Extras. */
    private ModificadorGrupo grupoDeExtras(Long grupoId, User actor) {
        ModificadorGrupo grupo = findById(grupoId, actor);
        if (!Boolean.TRUE.equals(grupo.getIsActive())) throw new IllegalArgumentException("Ese grupo ya no existe");
        if (!Boolean.TRUE.equals(grupo.getEsExtras())) {
            throw new IllegalStateException("\"" + grupo.getNombre() + "\" no es un grupo de extras: su precio se cambia en Platillos");
        }
        return grupo;
    }

    @Transactional
    public void delete(Long id, User actor) {
        ModificadorGrupo grupo = findById(id, actor);
        grupo.setIsActive(false);
        grupo.setDeletedBy(actor);
        grupo.setDeletedAt(LocalDateTime.now());
        modificadorGrupoRepository.save(grupo);
    }
}
