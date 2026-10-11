package com.nexora.restopos.dto;

import com.nexora.restopos.model.AppSection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

/**
 * Payload para crear o actualizar un {@code Role} ({@code POST}/{@code PUT /api/roles/**}).
 * Un {@code Role} pertenece siempre a una sola tienda (no es global): define el conjunto
 * de {@link AppSection} a las que dan acceso los usuarios que lo tienen asignado, es decir,
 * qué módulos de la app (Dashboard, Menú, Mesas, Órdenes, Cortes de caja, Ventas, Reportes,
 * Usuarios, Roles) puede ver y usar ese rol.
 */
@Data
public class RoleRequest {
    // max=40: debe coincidir con Role.name (VARCHAR(40)) — angosto a propósito en la base,
    // así que sin este tope un nombre de rol un poco largo truena al guardar con un error crudo.
    @NotBlank
    @Size(max = 40, message = "El nombre del rol no puede tener más de 40 caracteres")
    private String name;

    // max=200: debe coincidir con Role.description (VARCHAR(200)).
    @Size(max = 200, message = "La descripción no puede tener más de 200 caracteres")
    private String description;

    // Un rol sin ninguna sección no tiene ningún uso (no daría acceso a nada de la app),
    // así que se exige al menos una — a diferencia de otros campos opcionales, aquí un
    // conjunto vacío/null es un error de captura, no un valor válido.
    @NotEmpty(message = "Selecciona al menos una sección")
    private Set<AppSection> sections;
}
