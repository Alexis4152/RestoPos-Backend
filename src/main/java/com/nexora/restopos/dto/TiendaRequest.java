package com.nexora.restopos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Payload para crear o actualizar una {@code Tienda} (sucursal/negocio) desde el panel de
 * plataforma ({@code POST}/{@code PUT /api/tiendas/**}, para SUPER_ADMIN/SUPERVISOR). Solo
 * cubre el nombre de la tienda; la personalización de marca se maneja aparte con
 * {@link TiendaThemeRequest} y los datos fiscales con {@link TiendaInfoRequest}.
 */
@Data
public class TiendaRequest {
    // max=150: debe coincidir con Tienda.name (VARCHAR(150)) para dar un 400 limpio en vez
    // de que Hibernate truene con un error de base de datos si se manda un nombre más largo.
    @NotBlank
    @Size(max = 150)
    private String name;

    // Solo lo puede fijar SUPER_ADMIN: id de un usuario con rol SUPERVISOR a cargo de esta
    // tienda. Null = no cambiar el supervisor actual (un SUPERVISOR creando su propia tienda
    // no necesita mandarlo, queda auto-asignado).
    private Long supervisorId;
}
