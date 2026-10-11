package com.nexora.restopos.dto;

import com.nexora.restopos.model.ModificadorGrupoTipo;
import com.nexora.restopos.model.ModificadorOpcion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Un {@link com.nexora.restopos.model.ModificadorGrupo} de OTRO producto, tal como lo ve el
 * selector de "Copiar de otro producto" en Menú: qué platillo lo tiene (para poder distinguir
 * "Término de cocción" del Bistec del de la Milanesa) y sus opciones — ver
 * {@code ModificadorGrupoService#findCopiablesDeLaMismaCategoria}.
 * <p>
 * Deliberadamente no es el {@link com.nexora.restopos.model.ModificadorGrupo} tal cual: su
 * {@code producto} lleva {@code @JsonBackReference} (para cortar el ciclo Product↔ModificadorGrupo
 * al serializar el catálogo normal) y por eso nunca llegaría al frontend en ese endpoint.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ModificadorGrupoCopiableView {
    private Long id;
    private String nombre;
    private ModificadorGrupoTipo tipo;
    private Boolean obligatorio;
    private String productoNombre;
    private List<ModificadorOpcion> opciones;
}
