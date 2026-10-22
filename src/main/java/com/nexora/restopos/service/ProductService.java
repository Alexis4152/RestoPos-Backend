package com.nexora.restopos.service;

import com.nexora.restopos.dto.InventoryAdjustRequest;
import com.nexora.restopos.dto.ProductRequest;
import com.nexora.restopos.exception.FieldConflictException;
import com.nexora.restopos.model.MovementType;
import com.nexora.restopos.model.Category;
import com.nexora.restopos.model.InventoryMovement;
import com.nexora.restopos.model.Product;
import com.nexora.restopos.model.Tienda;
import com.nexora.restopos.model.User;
import com.nexora.restopos.repository.InventoryMovementRepository;
import com.nexora.restopos.repository.ProductRepository;
import com.nexora.restopos.repository.TiendaRepository;
import com.nexora.restopos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * CRUD de productos y control de inventario, con aislamiento por tienda (multi-tenancy).
 *
 * <p>Cada producto pertenece a una sola tienda y a una {@link Category} de esa misma
 * tienda. Todo cambio de stock (alta con stock inicial, ajuste manual, o los que
 * disparan {@link SaleService}/cancelaciones) queda respaldado por un {@link
 * InventoryMovement} que conserva el stock anterior y el nuevo, para tener trazabilidad
 * completa de por qué cambió el inventario. La eliminación de productos es siempre
 * borrado suave.</p>
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryService categoryService;
    private final InventoryMovementRepository movementRepository;
    private final TiendaRepository tiendaRepository;
    private final TenantScope tenantScope;
    private final AuditoriaService auditoriaService;
    private final com.nexora.restopos.repository.OrdenDetalleModificadorRepository ordenDetalleModificadorRepository;
    private final com.nexora.restopos.repository.OrdenDetalleRepository ordenDetalleRepository;

    // Sugerencias del modo rápido: solo se preselecciona una opción que domina claramente.
    private static final int SUGERENCIA_DIAS = 90;
    private static final java.math.BigDecimal SUGERENCIA_MIN_PIEZAS = java.math.BigDecimal.valueOf(5);
    private static final double SUGERENCIA_MIN_PROPORCION = 0.6;

    /**
     * Búsqueda paginada de productos activos, con filtros combinables.
     *
     * @param q texto de búsqueda (nombre/código de barras, según implemente el repositorio), o null
     * @param categoryId filtro por categoría, o null para no filtrar
     * @param lowStock si es true, limita a productos con stock por debajo de su mínimo; null/false no filtra
     * @param pageable paginación y orden solicitados
     * @param actor usuario que consulta; acota el resultado a su tienda
     * @return página de productos activos que cumplen los filtros
     */
    public Page<Product> search(String q, Long categoryId, Boolean lowStock, boolean soloInventario, Pageable pageable, User actor) {
        return productRepository.searchActive(q, categoryId, lowStock, soloInventario, tenantScope.scopeId(actor), pageable);
    }

    /**
     * Lista todos los productos activos visibles para el actor.
     *
     * @param actor usuario que consulta; acota el resultado a su tienda
     * @return productos activos dentro del alcance del actor
     */
    public List<Product> findAll(User actor) {
        return productRepository.findAllActive(tenantScope.scopeId(actor));
    }

    /**
     * Busca un producto por nombre/código en las tiendas "hermanas" de la del actor — las
     * que comparten el mismo SUPERVISOR (ver {@link TiendaRepository#findSiblingTiendas}),
     * sin incluir la propia. A propósito NO usa {@link TenantScope#scopeId}: esa regla es
     * "solo SUPER_ADMIN/SUPERVISOR ven más de una tienda", pero esta consulta es exactamente
     * la EXCEPCIÓN — cualquier rol (mesero, cajero, admin) puede usarla para saber si una
     * sucursal del mismo grupo tiene existencias antes de mandar a un cliente para allá, sin
     * que eso le dé visibilidad sobre nada más de esas tiendas.
     *
     * @param q texto de búsqueda (nombre o código de barras) — a diferencia de {@link
     *          #search}, no puede venir vacío: sin texto no tendría sentido listar el
     *          catálogo completo de tiendas ajenas
     * @param actor usuario que consulta; su tienda actual (ver {@link
     *              TenantScope#tiendaForWrite}) determina el grupo de tiendas hermanas
     * @param pageable paginación solicitada
     * @return productos con stock disponible (&gt;0) en tiendas hermanas que calzan con
     *         {@code q}, o una página vacía si el actor no tiene tienda elegida o esa
     *         tienda no tiene supervisor asignado (sin "hermanas" con quién comparar)
     */
    public Page<Product> searchSiblingStock(String q, User actor, Pageable pageable) {
        if (q == null || q.isBlank()) return Page.empty(pageable);
        Tienda myTienda = tenantScope.tiendaForWrite(actor);
        if (myTienda == null) return Page.empty(pageable);
        List<Tienda> siblings = tiendaRepository.findSiblingTiendas(myTienda.getId());
        if (siblings.isEmpty()) return Page.empty(pageable);
        List<Long> siblingIds = siblings.stream().map(Tienda::getId).toList();
        return productRepository.searchAcrossTiendas(siblingIds, q, pageable);
    }

    /**
     * Busca un producto activo por su código de barras exacto, dentro de la tienda del
     * actor — pensado para los flujos de lector de código de barras en Inventario.
     *
     * <p>A diferencia de {@link #findById}, NO lanza excepción si no existe: un código
     * desconocido es un resultado normal al escanear (no un error), y el llamador decide
     * qué hacer (ej. ofrecer dar de alta un producto nuevo con ese código ya precargado).</p>
     *
     * @param barcode código de barras exacto a buscar
     * @param actor usuario que consulta; acota la búsqueda a su tienda
     * @return el producto encontrado, o {@code null} si ningún producto activo de su
     *         tienda tiene ese código
     */
    public Product findByBarcode(String barcode, User actor) {
        return productRepository.findByBarcodeExact(barcode, tenantScope.scopeId(actor)).orElse(null);
    }

    /**
     * Productos disponibles ordenados por más vendidos (para el buscador de la toma de
     * órdenes), opcionalmente filtrados por categoría.
     *
     * @param categoryId categoría a filtrar, o null para el ranking general
     * @param limit cantidad máxima de productos a devolver
     * @param actor usuario que consulta; acota el resultado a su tienda
     */
    public List<Product> topSelling(Long categoryId, int limit, User actor) {
        return productRepository.topSelling(categoryId, tenantScope.scopeId(actor), limit);
    }

    /**
     * Busca un producto por id sin validar a qué tienda pertenece. Uso interno; para
     * flujos con control de acceso usar {@link #findById(Long, User)}.
     *
     * @param id id del producto
     * @return el producto encontrado
     * @throws IllegalArgumentException si no existe
     */
    public Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado: " + id));
    }

    /**
     * Busca un producto por id validando que pertenezca a la tienda del actor.
     *
     * @param id id del producto
     * @param actor usuario que realiza la consulta
     * @return el producto encontrado, perteneciente al alcance del actor
     * @throws IllegalArgumentException si no existe o no pertenece a la tienda del actor
     */
    public Product findById(Long id, User actor) {
        Product p = findById(id);
        Long scope = tenantScope.scopeId(actor);
        if (scope != null && (p.getTienda() == null || !scope.equals(p.getTienda().getId()))) {
            throw new IllegalArgumentException("Producto no encontrado: " + id);
        }
        return p;
    }

    /**
     * Da de alta un producto nuevo en la tienda del actor.
     *
     * <p>Aplica valores por defecto cuando no vienen en el request: stock 0, stock
     * mínimo 5, unidad "pieza". Si el producto se crea con stock inicial mayor a cero,
     * se registra automáticamente un {@link InventoryMovement} de tipo {@code IN} con
     * motivo "Stock inicial", para que el historial de movimientos explique de dónde
     * salió ese stock.</p>
     *
     * @param req datos del producto nuevo, incluyendo el id de su categoría
     * @param actor usuario que lo crea; determina la tienda del producto (la que esté
     *              actuando, si es SUPER_ADMIN/SUPERVISOR — ver {@link
     *              TenantScope#tiendaForWrite}); queda registrado como {@code createdBy}
     * @return el producto creado
     * @throws IllegalArgumentException si la categoría no existe o no pertenece a la
     *         tienda del actor
     * @throws IllegalStateException si es un actor de plataforma sin ninguna tienda elegida
     * @throws FieldConflictException (campo {@code barcode}) si otro producto activo de la
     *         tienda ya usa ese código de barras
     */
    public Product create(ProductRequest req, User actor) {
        Tienda tienda = tenantScope.tiendaForWrite(actor);
        if (tienda == null && tenantScope.isPlatformActor(actor)) {
            throw new IllegalStateException("Elige una tienda para poder crear un producto");
        }
        Category cat = categoryService.findById(req.getCategoryId(), actor);
        Long tiendaId = tienda != null ? tienda.getId() : null;
        validateBarcodeUnique(req.getBarcode(), tiendaId, null);

        Product p = new Product();
        p.setName(req.getName());
        p.setDescription(req.getDescription());
        p.setBarcode(normalizeBarcode(req.getBarcode()));
        p.setPrice(req.getPrice());
        // 0 = sin costeo capturado (opcional en un menú de restaurante, a diferencia del retail)
        p.setCost(req.getCost() != null ? req.getCost() : java.math.BigDecimal.ZERO);
        p.setStock(req.getStock() != null ? req.getStock() : 0);
        // 0 = sin control de inventario para este platillo (no obligatorio en un menú de restaurante)
        p.setMinStock(req.getMinStock() != null ? req.getMinStock() : 0);
        p.setUnit(req.getUnit() != null ? req.getUnit() : "pieza");
        p.setImageUrl(req.getImageUrl());
        p.setDisponible(req.getDisponible() == null || req.getDisponible());
        p.setControlaInventario(Boolean.TRUE.equals(req.getControlaInventario()));
        if (!p.getControlaInventario()) { p.setStock(0); p.setMinStock(0); }

        p.setCategory(cat);
        p.setTienda(tienda);
        p.setIsActive(true);
        p.setCreatedBy(actor);
        Product saved = productRepository.save(p);

        if (saved.getStock() > 0) {
            recordMovement(saved, actor, MovementType.IN, saved.getStock(), 0, "Stock inicial");
        }
        return saved;
    }

    /**
     * Actualiza los datos de un producto existente. No modifica el stock: los cambios de
     * stock siempre pasan por {@link #adjustStock(Long, InventoryAdjustRequest, User)}
     * (o por una venta/cancelación) para quedar respaldados con su movimiento de inventario.
     *
     * @param id id del producto a actualizar
     * @param req nuevos valores, incluyendo el id de su categoría (puede cambiar de categoría)
     * @param actor usuario que hace el cambio; debe tener acceso a la tienda del
     *              producto; queda registrado como {@code updatedBy}
     * @return el producto actualizado
     * @throws IllegalArgumentException si el producto o la nueva categoría no existen o
     *         no pertenecen a la tienda del actor
     * @throws FieldConflictException (campo {@code barcode}) si otro producto activo de la
     *         tienda ya usa ese código de barras
     */
    public Product update(Long id, ProductRequest req, User actor) {
        Product p = findById(id, actor);
        Category cat = categoryService.findById(req.getCategoryId(), actor);
        Long tiendaId = p.getTienda() != null ? p.getTienda().getId() : null;
        validateBarcodeUnique(req.getBarcode(), tiendaId, p.getId());

        p.setName(req.getName());
        p.setDescription(req.getDescription());
        p.setBarcode(normalizeBarcode(req.getBarcode()));
        p.setPrice(req.getPrice());
        // conserva el costo existente si el formulario no lo envía (no lo pisa con null/0)
        if (req.getCost() != null) p.setCost(req.getCost());
        // Igual que el costo: si el formulario no lo manda (ej. Menú), se conserva el mínimo.
        if (req.getMinStock() != null) p.setMinStock(req.getMinStock());
        p.setUnit(req.getUnit() != null ? req.getUnit() : "pieza");
        // la foto se administra aparte (ProductImageService/{id}/image); no se pisa aquí
        if (req.getDisponible() != null) p.setDisponible(req.getDisponible());
        // El stock no se toca al apagarlo: si lo vuelven a activar, conserva lo que tenía.
        if (req.getControlaInventario() != null) p.setControlaInventario(req.getControlaInventario());
        p.setCategory(cat);
        p.setUpdatedBy(actor);
        return productRepository.save(p);
    }

    /** Sugerencias del modo rápido para un producto: opciones preseleccionadas y notas frecuentes. */
    public record Sugerencias(java.util.Map<Long, Long> opciones, List<String> notas) {}

    /**
     * Opciones sugeridas (ver {@link #opcionesSugeridas}) y hasta 6 notas que se han escrito al
     * menos 2 veces para este producto en los últimos 90 días ("Sin cebolla", "Poco picante"),
     * separando las que se escribieron juntas con coma. Las notas nunca se preseleccionan: el
     * mesero las toca solo si el cliente las pide.
     */
    public Sugerencias sugerencias(Long id, User actor) {
        Product p = findById(id, actor);
        java.util.Map<String, long[]> conteo = new java.util.LinkedHashMap<>();
        java.util.Map<String, String> texto = new java.util.HashMap<>();
        for (Object[] f : ordenDetalleRepository.contarNotas(p.getId(), java.time.LocalDateTime.now().minusDays(SUGERENCIA_DIAS))) {
            long veces = ((Number) f[1]).longValue();
            for (String parte : f[0].toString().split("[,;\n]")) {
                String nota = parte.trim();
                if (nota.isEmpty() || nota.length() > 40) continue;
                String clave = nota.toLowerCase();
                texto.putIfAbsent(clave, Character.toUpperCase(nota.charAt(0)) + nota.substring(1));
                conteo.computeIfAbsent(clave, k -> new long[1])[0] += veces;
            }
        }
        List<String> notas = conteo.entrySet().stream()
                .filter(e -> e.getValue()[0] >= 2)
                .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                .limit(6)
                .map(e -> texto.get(e.getKey()))
                .toList();
        return new Sugerencias(opcionesSugeridas(p), notas);
    }

    /**
     * Opción sugerida por grupo para el modo rápido de toma de orden: en cada grupo
     * OBLIGATORIO de opción única, la opción que más se pidió en los últimos 90 días, solo si
     * domina (al menos 60 % de las piezas y al menos 5 piezas). Si están parejas, el platillo es
     * nuevo o la opción ya no existe, ese grupo no lleva sugerencia y el mesero elige. Nunca
     * sugiere grupos opcionales ni de opción múltiple (extras, notas): eso lo decide el cliente.
     *
     * @return mapa {@code grupoId -> opcionId}; vacío si no hay nada que sugerir
     */
    private java.util.Map<Long, Long> opcionesSugeridas(Product p) {
        java.util.Map<Long, Long> out = new java.util.LinkedHashMap<>();
        List<Object[]> filas = ordenDetalleModificadorRepository.contarOpciones(p.getId(), java.time.LocalDateTime.now().minusDays(SUGERENCIA_DIAS));
        if (filas.isEmpty()) return out;
        for (com.nexora.restopos.model.ModificadorGrupo g : p.getModificadorGrupos()) {
            if (!Boolean.TRUE.equals(g.getIsActive()) || !Boolean.TRUE.equals(g.getObligatorio())
                    || g.getTipo() != com.nexora.restopos.model.ModificadorGrupoTipo.UNICO) continue;
            java.util.Map<String, java.math.BigDecimal> porOpcion = new java.util.HashMap<>();
            java.math.BigDecimal total = java.math.BigDecimal.ZERO;
            for (Object[] f : filas) {
                if (f[0] == null || !g.getNombre().equalsIgnoreCase(f[0].toString())) continue;
                java.math.BigDecimal n = new java.math.BigDecimal(f[2].toString());
                porOpcion.merge(f[1].toString().toLowerCase(), n, java.math.BigDecimal::add);
                total = total.add(n);
            }
            if (total.compareTo(SUGERENCIA_MIN_PIEZAS) < 0) continue;
            var top = porOpcion.entrySet().stream().max(java.util.Map.Entry.comparingByValue()).orElse(null);
            if (top == null || top.getValue().doubleValue() / total.doubleValue() < SUGERENCIA_MIN_PROPORCION) continue;
            g.getOpciones().stream()
                    .filter(o -> Boolean.TRUE.equals(o.getIsActive()) && o.getNombre().equalsIgnoreCase(top.getKey()))
                    .findFirst()
                    .ifPresent(o -> out.put(g.getId(), o.getId()));
        }
        return out;
    }

    /**
     * Marca un platillo como agotado ("ya no hay para hacerlo") o de nuevo disponible, sin
     * tocar nada más del producto. Lo puede hacer el mesero desde la orden (no solo el admin):
     * mientras esté agotado no se puede agregar a órdenes ni pedir en la página web.
     *
     * @param id id del producto; debe ser de la tienda del actor
     * @param disponible false = agotado, true = ya hay
     * @param actor usuario que lo cambia (queda en la auditoría)
     * @return el producto actualizado
     */
    @Transactional
    public Product cambiarDisponible(Long id, boolean disponible, User actor) {
        Product p = findById(id, actor);
        if (!Boolean.TRUE.equals(p.getIsActive())) throw new IllegalArgumentException("Producto no encontrado");
        boolean antes = !Boolean.FALSE.equals(p.getDisponible());
        if (antes == disponible) return p;

        p.setDisponible(disponible);
        p.setUpdatedBy(actor);
        Product saved = productRepository.save(p);
        auditoriaService.log(actor, disponible ? "PRODUCTO_DISPONIBLE" : "PRODUCTO_AGOTADO", "Product", p.getId(),
                antes ? "Disponible" : "Agotado", disponible ? "Disponible" : "Agotado", p.getName());
        return saved;
    }

    /**
     * Ajusta manualmente el stock de un producto (entrada o salida) y registra el
     * movimiento de inventario correspondiente.
     *
     * <p>Una cantidad positiva es una entrada ({@code IN}); una cantidad negativa es una
     * salida ({@code OUT}). Solo un actor con privilegios de administrador (ADMIN, o un
     * SUPER_ADMIN/SUPERVISOR actuando sobre la tienda — ver {@link
     * TenantScope#isAdminLevel}) puede registrar salidas (quitar piezas) — cualquier otro
     * rol solo puede sumar stock. El resultado nunca puede dejar el stock en negativo.</p>
     *
     * @param id id del producto a ajustar
     * @param req cantidad a ajustar (positiva=entrada, negativa=salida) y motivo del ajuste
     * @param actor usuario que hace el ajuste; debe tener acceso a la tienda del
     *              producto; queda registrado en el movimiento de inventario
     * @return el producto con el stock ya actualizado
     * @throws IllegalStateException si un actor sin privilegios de administrador intenta
     *         quitar piezas
     * @throws FieldConflictException (campo {@code quantity}) si el ajuste dejaría el
     *         stock en negativo, o si el stock resultante desbordaría un {@code int}
     */
    @Transactional
    public Product adjustStock(Long id, InventoryAdjustRequest req, User actor) {
        if (req.getQuantity() < 0 && !tenantScope.isAdminLevel(actor)) {
            throw new IllegalStateException("Solo un administrador puede quitar piezas del inventario");
        }
        Product p = findById(id, actor);
        if (!Boolean.TRUE.equals(p.getControlaInventario())) {
            throw new IllegalStateException("\"" + p.getName() + "\" no lleva inventario: actívalo en el producto (\"Lleva inventario\") para ajustar su stock");
        }
        int previous = p.getStock();
        // En long, NO en int: "previous + quantity" con aritmética int normal se desborda en
        // silencio si la suma pasa de Integer.MAX_VALUE (envuelve a un negativo grande), lo
        // que antes disparaba "Stock insuficiente" con un valor de entrada alto — un mensaje
        // totalmente equivocado para lo que en realidad es un desbordamiento, no falta de stock.
        long newStockLong = (long) previous + req.getQuantity();
        if (newStockLong < 0) {
            throw new FieldConflictException("quantity", "Stock insuficiente");
        }
        if (newStockLong > Integer.MAX_VALUE) {
            throw new FieldConflictException("quantity", "El número es excesivamente grande — el máximo permitido es 2,147,483,647");
        }
        int newStock = (int) newStockLong;
        p.setStock(newStock);
        productRepository.save(p);

        MovementType type = req.getQuantity() >= 0 ? MovementType.IN : MovementType.OUT;
        recordMovement(p, actor, type, Math.abs(req.getQuantity()), previous, req.getReason());
        return p;
    }

    /**
     * Desactiva (borrado suave) un producto: marca {@code isActive=false} y registra
     * quién y cuándo lo eliminó, sin borrar la fila ni su historial de movimientos.
     *
     * @param id id del producto a desactivar
     * @param actor usuario que lo desactiva; debe tener acceso a la tienda del producto
     */
    public void deactivate(Long id, User actor) {
        Product p = findById(id, actor);
        p.setIsActive(false);
        p.setDeletedBy(actor);
        p.setDeletedAt(java.time.LocalDateTime.now());
        productRepository.save(p);
    }

    // Sin esto, un formulario que deja el campo vacío manda "" (no null), y dos productos
    // con barcode="" chocarían entre sí en cuanto se agregara alguna restricción de
    // unicidad — a diferencia de NULL, que nunca se considera igual a otro NULL. Guardar
    // NULL en vez de "" es lo que de verdad permite "todos los que sean" sin código de barras.
    /**
     * Normaliza un código de barras capturado en un formulario: cadena vacía o solo
     * espacios se convierte a {@code null}; cualquier otro valor se regresa tal cual.
     */
    private String normalizeBarcode(String barcode) {
        return (barcode == null || barcode.isBlank()) ? null : barcode;
    }

    /**
     * Valida que un código de barras (si viene) no esté ya en uso por otro producto
     * activo de la misma tienda — la unicidad es por tienda, no global, ya que dos
     * tiendas distintas pueden vender legítimamente el mismo producto con el mismo código.
     *
     * @param barcode código a validar; si es {@code null} o está en blanco, no valida nada
     * @param tiendaId tienda contra la que se valida
     * @param excludeProductId id del propio producto a excluir de la validación (al
     *                         editar, para no chocar contra sí mismo); {@code null} al crear
     * @throws FieldConflictException (campo {@code barcode}) si otro producto activo de la
     *         tienda ya usa ese código — así el frontend lo muestra debajo de ese input
     */
    private void validateBarcodeUnique(String barcode, Long tiendaId, Long excludeProductId) {
        if (barcode == null || barcode.isBlank()) return;
        Product existing = productRepository.findByBarcodeExact(barcode, tiendaId).orElse(null);
        if (existing != null && !existing.getId().equals(excludeProductId)) {
            throw new FieldConflictException("barcode", "Ya existe un producto con ese código de barras: " + existing.getName());
        }
    }

    /**
     * Registra un movimiento de inventario asociado a un cambio de stock de un producto,
     * conservando el stock previo y el nuevo para trazabilidad.
     *
     * @param product producto afectado
     * @param actor usuario responsable del movimiento
     * @param type tipo de movimiento ({@code IN} o {@code OUT})
     * @param quantity cantidad movida (siempre positiva; el signo lo da {@code type})
     * @param previous stock antes del movimiento
     * @param reason motivo del movimiento, para mostrar en el historial
     */
    private void recordMovement(Product product, User actor, MovementType type,
                                 int quantity, int previous, String reason) {
        InventoryMovement mv = new InventoryMovement();
        mv.setProduct(product);
        mv.setUser(actor);
        mv.setType(type);
        mv.setQuantity(quantity);
        mv.setPreviousStock(previous);
        mv.setNewStock(previous + (type == MovementType.IN ? quantity : -quantity));
        mv.setReason(reason);
        movementRepository.save(mv);
    }
}
