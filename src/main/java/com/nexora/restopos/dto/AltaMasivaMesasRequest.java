package com.nexora.restopos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Alta de varias mesas y/o zonas de un golpe ({@code POST /api/tables/bulk}). Cada grupo es una
 * zona (existente por {@code zonaId}, o nueva/existente por {@code zonaNombre}) con N mesas
 * numeradas en secuencia: {@code prefijo + desde}, {@code prefijo + (desde+1)}, ... Un grupo
 * con {@code cantidad = 0} solo crea la zona. Todo o nada: si una mesa choca, no se crea nada.
 */
@Data
public class AltaMasivaMesasRequest {

    @NotEmpty(message = "Agrega al menos una zona")
    @Size(max = 30, message = "Máximo 30 zonas a la vez")
    @Valid
    private List<Grupo> grupos;

    @Data
    public static class Grupo {
        /** Zona existente. Si viene, {@code zonaNombre} se ignora. */
        private Long zonaId;

        /** Zona nueva (o una existente con ese mismo nombre, sin distinguir mayúsculas). */
        @Size(max = 100, message = "El nombre de la zona no puede tener más de 100 caracteres")
        private String zonaNombre;

        @Min(value = 0, message = "La cantidad de mesas no puede ser negativa")
        @Max(value = 200, message = "Máximo 200 mesas por zona a la vez")
        private int cantidad;

        /** Número de la primera mesa (las demás siguen en orden). */
        @Min(value = 0, message = "El número inicial no puede ser negativo")
        @Max(value = 99999, message = "El número inicial es demasiado grande")
        private int desde = 1;

        /** Texto antes del número (ej. "T" → T1, T2...). Vacío = solo el número. */
        @Size(max = 10, message = "El prefijo no puede tener más de 10 caracteres")
        private String prefijo;

        /** Lugares de cada mesa (null = sin capturar). */
        @Min(value = 1, message = "Los lugares deben ser al menos 1")
        @Max(value = 100, message = "Máximo 100 lugares por mesa")
        private Integer capacidad;
    }
}
