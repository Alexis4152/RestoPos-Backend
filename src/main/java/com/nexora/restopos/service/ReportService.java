package com.nexora.restopos.service;

import com.nexora.restopos.model.Product;
import com.nexora.restopos.model.User;
import com.nexora.restopos.repository.InventoryMovementRepository;
import com.nexora.restopos.repository.OrdenDetalleRepository;
import com.nexora.restopos.repository.PagoRepository;
import com.nexora.restopos.repository.ProductRepository;
import com.nexora.restopos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Genera los datos de reportería y dashboard: resumen de ventas (a partir de {@link
 * com.nexora.restopos.model.Pago}, no de una tabla de ventas separada), platillos más pedidos,
 * ventas por día, estado de inventario (stock bajo) y movimientos de un producto.
 *
 * <p>Todas las consultas se acotan a la tienda del actor mediante {@link TenantScope} (o a
 * todas si es SUPER_ADMIN). Los rangos de fecha se reciben como {@link LocalDate} inclusivos y
 * se convierten internamente a un rango {@code [start, end)} en {@link LocalDateTime}.</p>
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final PagoRepository pagoRepository;
    private final OrdenDetalleRepository ordenDetalleRepository;
    private final InventoryMovementRepository movementRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;
    private final TenantScope tenantScope;

    public Map<String, Object> salesSummary(LocalDate from, LocalDate to, User actor) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        Long tiendaId = tenantScope.scopeId(actor);

        BigDecimal total = pagoRepository.totalBetween(start, end, tiendaId);
        long count = pagoRepository.countBetween(start, end, tiendaId);

        Map<String, Object> map = new HashMap<>();
        map.put("totalSales", total != null ? total : BigDecimal.ZERO);
        map.put("totalTransactions", count);
        map.put("averageTicket", count > 0 && total != null
                ? total.divide(BigDecimal.valueOf(count), 2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        map.put("from", from);
        map.put("to", to);
        return map;
    }

    public List<Object[]> topProducts(LocalDate from, LocalDate to, int limit, User actor) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        return ordenDetalleRepository.topProductosBetween(start, end, limit, tenantScope.scopeId(actor));
    }

    public List<Object[]> salesByDay(LocalDate from, LocalDate to, User actor) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        return pagoRepository.salesByDay(start, end, tenantScope.scopeId(actor));
    }

    public Map<String, Object> inventoryStatus(User actor) {
        List<Product> lowStock = productRepository.findLowStockForTienda(tenantScope.scopeId(actor));
        Map<String, Object> map = new HashMap<>();
        map.put("lowStockProducts", lowStock);
        return map;
    }

    public List<Object[]> movementsByProduct(Long productId, LocalDate from, LocalDate to, User actor) {
        productService.findById(productId, actor); // valida que el producto sea de la tienda del actor
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        return movementRepository.findByProductIdAndCreatedAtBetween(productId, start, end);
    }
}
