package com.nexora.restopos.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Map;

/**
 * Ordenamiento de tablas paginadas desde los encabezados del frontend ({@code ?sort=clave&dir=asc|desc}).
 *
 * <p>La clave que manda el cliente NUNCA se usa tal cual: se traduce con una lista blanca
 * por tabla ({@code allowed}: clave pública → propiedad JPQL, ej. {@code "seller" →
 * "su.name"}). Una clave desconocida cae al orden por defecto de la tabla, así que no se
 * puede ordenar por campos internos ni inyectar nada en la consulta. Siempre se agrega
 * {@code id} como desempate para que la paginación sea estable cuando hay valores repetidos.</p>
 */
public final class SortParams {

    private SortParams() {
    }

    /**
     * @param page     página (base 0)
     * @param size     tamaño de página
     * @param sort     clave pública de la columna, o null
     * @param dir      "asc" o "desc" (cualquier otro valor = asc)
     * @param allowed  clave pública → propiedad a ordenar
     * @param fallback orden por defecto de la tabla (si {@code sort} no viene o no es válido)
     */
    public static Pageable page(int page, int size, String sort, String dir, Map<String, String> allowed, Sort fallback) {
        return PageRequest.of(page, size, resolve(sort, dir, allowed, fallback).and(Sort.by(Sort.Direction.DESC, "id")));
    }

    /** Solo el {@link Sort} (sin desempate), para consultas que arman su propio ORDER BY. */
    public static Sort resolve(String sort, String dir, Map<String, String> allowed, Sort fallback) {
        String property = sort != null ? allowed.get(sort) : null;
        if (property == null) return fallback;
        return Sort.by("desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC, property);
    }
}
