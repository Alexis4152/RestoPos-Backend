package com.nexora.restopos.util;

/**
 * Dirección de entrega capturada en partes (calle y número, colonia, código postal) — en el
 * punto de venta, en la página web y en la cuenta del cliente. Además de las partes se guarda
 * la dirección completa ya armada ({@link Partes#completa()}) en la columna {@code direccion}
 * de siempre, que es la que usan el repartidor, el mapa, los avisos y los tickets.
 */
public final class Direccion {

    private Direccion() {}

    public static final int MAX_CALLE = 100;
    public static final int MAX_COLONIA = 70;

    public record Partes(String calle, String colonia, String codigoPostal, String completa) {}

    /**
     * Valida y arma la dirección. Si no llega ninguna parte se acepta {@code direccionLibre}
     * (pantallas viejas que mandan la dirección en un solo texto).
     *
     * @param obligatoria si falta todo, lanza error (si no, devuelve {@code null})
     * @throws IllegalArgumentException con un mensaje listo para mostrarle al usuario
     */
    public static Partes resolver(String calle, String colonia, String codigoPostal, String direccionLibre, boolean obligatoria) {
        String c = limpio(calle), col = limpio(colonia), cp = limpio(codigoPostal);
        if (c == null && col == null && cp == null) {
            String libre = limpio(direccionLibre);
            if (libre != null) {
                if (libre.length() > 200) throw new IllegalArgumentException("La dirección no puede tener más de 200 caracteres");
                return new Partes(null, null, null, libre);
            }
            if (obligatoria) throw new IllegalArgumentException("La dirección de entrega es obligatoria");
            return null;
        }
        if (c == null) throw new IllegalArgumentException("Escribe la calle y el número");
        if (col == null) throw new IllegalArgumentException("Escribe la colonia");
        if (cp == null) throw new IllegalArgumentException("Escribe el código postal");
        if (!cp.matches("\\d{5}")) throw new IllegalArgumentException("El código postal debe tener 5 dígitos");
        if (c.length() > MAX_CALLE) throw new IllegalArgumentException("La calle no puede tener más de " + MAX_CALLE + " caracteres");
        if (col.length() > MAX_COLONIA) throw new IllegalArgumentException("La colonia no puede tener más de " + MAX_COLONIA + " caracteres");
        // "Col." solo si el cliente no lo escribió ya ("Col. Centro", "Colonia Centro").
        String colTexto = col.toLowerCase().startsWith("col") ? col : "Col. " + col;
        return new Partes(c, col, cp, c + ", " + colTexto + ", C.P. " + cp);
    }

    private static String limpio(String s) {
        if (s == null) return null;
        String t = s.trim().replaceAll("\\s+", " ");
        return t.isEmpty() ? null : t;
    }
}
