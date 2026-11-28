package com.nexora.restopos.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Borrado DEFINITIVO de una tienda y de todo lo que depende de ella (usuarios, órdenes, cuentas,
 * pagos, cortes, platillos, mesas, configuración...). No usa una lista fija de tablas: lee las
 * llaves foráneas de la base de datos y recorre el árbol desde {@code tiendas}, así lo que se agregue
 * en el futuro también se borra.
 * <ul>
 *   <li>Todo renglón que apunta a la tienda (tienda_id) se borra.</li>
 *   <li>Lo que depende de un renglón borrado con llave obligatoria (NOT NULL) también se borra.</li>
 *   <li>Lo que depende con llave opcional (NULL permitido) solo se desliga (se pone en NULL): así no
 *       se borra nada de otras tiendas que por casualidad apunte a algo de esta.</li>
 * </ul>
 * Todo en una sola transacción: si algo falla, no se borra nada. Las imágenes en disco no se tocan.
 */
@Service
@RequiredArgsConstructor
public class TiendaPurgeService {

    private static final int PROFUNDIDAD_MAXIMA = 40;

    private final JdbcTemplate jdbc;

    private record Fk(String hija, String columna, String padre, String columnaPadre, boolean opcional) {}

    /**
     * Borra la tienda y todo lo que depende de ella.
     *
     * @return cuántos renglones se borraron por tabla
     */
    @Transactional
    public Map<String, Integer> purgar(long tiendaId) {
        List<Fk> fks = llavesForaneas();
        Map<String, Integer> conteo = new LinkedHashMap<>();
        for (Fk fk : fks) {
            if (fk.padre().equals("tiendas") && !fk.hija().equals("tiendas")) {
                borrar(fk.hija(), q(fk.columna()) + " = " + tiendaId, fks, conteo, 0);
            }
        }
        borrar("tiendas", "id = " + tiendaId, fks, conteo, 0);
        return conteo;
    }

    private void borrar(String tabla, String donde, List<Fk> fks, Map<String, Integer> conteo, int profundidad) {
        if (profundidad > PROFUNDIDAD_MAXIMA) {
            throw new IllegalStateException("No se pudo eliminar la tienda: relaciones demasiado profundas en " + tabla);
        }
        for (Fk fk : fks) {
            if (!fk.padre().equals(tabla)) continue;
            String apuntan = q(fk.columna()) + " IN (SELECT " + q(fk.columnaPadre()) + " FROM " + q(tabla) + " WHERE " + donde + ")";
            if (fk.opcional() || fk.hija().equals(tabla)) {
                // Llave opcional (o la tabla apunta a sí misma): se desliga en vez de borrar.
                if (fk.opcional()) jdbc.update("UPDATE " + q(fk.hija()) + " SET " + q(fk.columna()) + " = NULL WHERE " + apuntan);
                continue;
            }
            borrar(fk.hija(), apuntan, fks, conteo, profundidad + 1);
        }
        int n = jdbc.update("DELETE FROM " + q(tabla) + " WHERE " + donde);
        if (n > 0) conteo.merge(tabla, n, Integer::sum);
    }

    /** Llaves foráneas de una sola columna del esquema actual. */
    private List<Fk> llavesForaneas() {
        return jdbc.query("""
                SELECT ch.relname AS hija, ca.attname AS columna, pa_t.relname AS padre, pa.attname AS columna_padre,
                       NOT ca.attnotnull AS opcional
                  FROM pg_constraint c
                  JOIN pg_class ch ON ch.oid = c.conrelid
                  JOIN pg_class pa_t ON pa_t.oid = c.confrelid
                  JOIN pg_namespace ns ON ns.oid = ch.relnamespace
                  JOIN pg_attribute ca ON ca.attrelid = c.conrelid AND ca.attnum = c.conkey[1]
                  JOIN pg_attribute pa ON pa.attrelid = c.confrelid AND pa.attnum = c.confkey[1]
                 WHERE c.contype = 'f' AND ns.nspname = current_schema() AND array_length(c.conkey, 1) = 1
                """,
                (rs, i) -> new Fk(rs.getString("hija"), rs.getString("columna"), rs.getString("padre"),
                        rs.getString("columna_padre"), rs.getBoolean("opcional")));
    }

    private static String q(String identificador) {
        return "\"" + identificador.replace("\"", "\"\"") + "\"";
    }
}
