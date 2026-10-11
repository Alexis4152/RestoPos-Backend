package com.nexora.restopos.model;

/**
 * Catálogo de los módulos/pantallas de la aplicación sobre los que se puede otorgar
 * acceso mediante el sistema de roles (RBAC).
 * <p>
 * Cada {@link Role} guarda un {@code Set<AppSection>} con las secciones a las que da
 * acceso; el frontend usa esta lista para decidir qué menús/rutas mostrarle al usuario
 * autenticado y el backend la usa para autorizar las peticiones a cada módulo.
 */
public enum AppSection {
    /** Pantalla de inicio con indicadores/resumen general de la tienda. */
    DASHBOARD,
    /** Menú: categorías y productos/platillos. */
    MENU,
    /** Tablero de mesas: estado, apertura y detalle de cada mesa. */
    TABLES,
    /** Toma de órdenes, comensales y su historial. */
    ORDERS,
    /** Apertura, cierre y consulta de cortes de caja, y movimientos manuales de efectivo. */
    CASH_CUTS,
    /** Reportes (ej. total de ventas por día) y exportación de información. */
    REPORTS,
    /** Administración de usuarios de la tienda. */
    USERS,
    /** Administración de roles y sus permisos por sección. */
    ROLES,
    /** Consulta de lo que se ha vendido (cuentas cobradas/canceladas), filtrable por fecha, estado y método de pago. */
    SALES,
    /** Pantalla de cocina: productos pedidos en las órdenes activas, agrupados por mesa, en el orden en que se pidieron. */
    KITCHEN,
    /** Órdenes para llevar (sin mesa): tablero de las activas, apertura de una nueva y su detalle simplificado (sin comensales, cobro solo por el total). */
    TAKEAWAY,
    /** Inventario: alta/edición/baja de productos, ajustes de stock, búsqueda entre tiendas hermanas y carga masiva por Excel. */
    INVENTORY,
    /** Entregas a domicilio (repartidor): tomar pedidos, entregarlos y cobrarlos en efectivo o transferencia. */
    DELIVERY
}
