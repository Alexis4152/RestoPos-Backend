package com.nexora.restopos.util;

import com.nexora.restopos.model.OrdenDetalleModificador;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agrupa los modificadores de una línea para mostrarlos: una opción repetida se guarda como
 * una fila por pieza (así los totales suman solos) y aquí se junta en "Cebolla x3".
 */
public final class ModsAgrupados {

    private ModsAgrupados() {}

    /** Una opción con cuántas veces se eligió; {@code precioExtra} es por pieza. */
    public record Mod(String grupoNombre, String nombre, BigDecimal precioExtra, int cantidad, boolean libre) {
        /** Lo que suman todas las piezas. */
        public BigDecimal total() {
            return precioExtra.multiply(BigDecimal.valueOf(cantidad));
        }

        /** "Cebolla" o "Cebolla x3". */
        public String nombreConCantidad() {
            return cantidad > 1 ? nombre + " x" + cantidad : nombre;
        }
    }

    /** Junta las filas iguales (mismo grupo, nombre, precio y tipo) respetando el orden en que aparecieron. */
    public static List<Mod> agrupar(List<OrdenDetalleModificador> mods) {
        Map<String, Mod> out = new LinkedHashMap<>();
        if (mods == null) return List.of();
        for (OrdenDetalleModificador m : mods) {
            BigDecimal precio = m.getPrecioExtra() != null ? m.getPrecioExtra() : BigDecimal.ZERO;
            boolean libre = Boolean.TRUE.equals(m.getLibre());
            String key = m.getGrupoNombre() + "\u0000" + m.getNombre() + "\u0000" + precio.stripTrailingZeros().toPlainString() + "\u0000" + libre;
            Mod prev = out.get(key);
            out.put(key, prev == null
                    ? new Mod(m.getGrupoNombre(), m.getNombre(), precio, 1, libre)
                    : new Mod(prev.grupoNombre(), prev.nombre(), prev.precioExtra(), prev.cantidad() + 1, libre));
        }
        return new ArrayList<>(out.values());
    }
}
