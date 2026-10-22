package com.nexora.restopos.service;

import com.nexora.restopos.model.InventoryMovement;
import com.nexora.restopos.model.MovementType;
import com.nexora.restopos.model.OrdenDetalle;
import com.nexora.restopos.model.Product;
import com.nexora.restopos.model.User;
import com.nexora.restopos.repository.InventoryMovementRepository;
import com.nexora.restopos.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Inventario de lo que se vende: SOLO para productos con "Lleva inventario"
 * ({@link Product#getControlaInventario()}); los demás no se tocan.
 * <ul>
 *   <li>Al agregar un producto a una orden (POS o página web) se descuenta su stock. No se
 *       puede pedir más de lo que hay ("Solo quedan N").</li>
 *   <li>Al cancelar el producto o la orden, o al bajarle la cantidad, se regresa.</li>
 *   <li>Con stock en 0 queda "sin inventario" (Product#isSinInventario): en el POS se ve con
 *       esa etiqueta y no se puede agregar; en la página web sale "No disponible". NO cambia
 *       {@code disponible}, que es solo el "Se acabó" manual.</li>
 * </ul>
 * Cada movimiento lleva la referencia {@code OD:<id de la línea>}: así una devolución nunca
 * regresa más de lo que esa línea descontó (p. ej. órdenes abiertas antes de existir esto).
 */
@Service
@RequiredArgsConstructor
public class InventarioVentaService {

    private final InventoryMovementRepository movementRepository;
    private final ProductRepository productRepository;
    private final EntityManager entityManager;

    private static String ref(OrdenDetalle linea) {
        return "OD:" + linea.getId();
    }

    private static int piezas(BigDecimal cantidad) {
        return cantidad == null ? 0 : cantidad.setScale(0, RoundingMode.CEILING).intValue();
    }

    /** Bloquea el producto y trae su stock real (dos ventas al mismo tiempo no se pisan). */
    private Product bloquear(Product p) {
        entityManager.flush();
        entityManager.refresh(p, LockModeType.PESSIMISTIC_WRITE);
        return p;
    }

    /** Descuenta lo vendido en una línea. @throws IllegalStateException si no alcanza el stock. */
    public void descontar(OrdenDetalle linea, BigDecimal cantidad, User actor) {
        Product p = linea.getProducto();
        int n = piezas(cantidad);
        if (p == null || n <= 0 || !Boolean.TRUE.equals(p.getControlaInventario())) return;
        bloquear(p);
        int antes = p.getStock();
        if (n > antes) {
            throw new IllegalStateException(antes <= 0
                    ? "\"" + p.getName() + "\" está sin inventario"
                    : "Solo quedan " + antes + " de \"" + p.getName() + "\"");
        }
        p.setStock(antes - n);
        productRepository.save(p);
        registrar(p, actor, MovementType.SALE, n, antes, "Venta · orden #" + linea.getOrden().getId(), ref(linea));
    }

    /**
     * Regresa al stock hasta {@code cantidad} piezas de una línea (cancelación o baja de
     * cantidad), sin pasar de lo que esa línea descontó.
     */
    public void devolver(OrdenDetalle linea, BigDecimal cantidad, User actor, String motivo) {
        Product p = linea.getProducto();
        int n = piezas(cantidad);
        if (p == null || n <= 0 || !Boolean.TRUE.equals(p.getControlaInventario())) return;
        int descontado = movementRepository.netoVendidoPorReferencia(ref(linea));
        n = Math.min(n, descontado);
        if (n <= 0) return;
        bloquear(p);
        int antes = p.getStock();
        p.setStock(antes + n);
        productRepository.save(p);
        registrar(p, actor, MovementType.IN, n, antes, motivo + " · orden #" + linea.getOrden().getId(), ref(linea));
    }

    private void registrar(Product p, User actor, MovementType tipo, int n, int antes, String motivo, String referencia) {
        InventoryMovement mv = new InventoryMovement();
        mv.setProduct(p);
        mv.setUser(actor); // null en pedidos de la página web (los hace el cliente)
        mv.setType(tipo);
        mv.setQuantity(n);
        mv.setPreviousStock(antes);
        mv.setNewStock(tipo == MovementType.SALE ? antes - n : antes + n);
        mv.setReason(motivo);
        mv.setReference(referencia);
        movementRepository.save(mv);
    }
}
