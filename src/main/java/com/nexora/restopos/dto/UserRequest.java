package com.nexora.restopos.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Payload para crear o actualizar un {@code User} ({@code POST}/{@code PUT /api/users/**},
 * requiere acceso a la sección USERS). El usuario queda ligado a la tienda del actor que
 * lo crea, salvo que quien lo crea sea SUPER_ADMIN (ver {@link #tiendaId}). La
 * "eliminación" de usuarios ({@code DELETE /api/users/{id}}) es siempre borrado suave
 * ({@code isActive=false} + auditoría de quién/cuándo), nunca se borra la fila.
 * <p>
 * {@code password} no lleva validación aquí a propósito: es opcional en actualización
 * (vacío = no cambiarla) pero obligatoria al crear, algo que {@code jakarta.validation} no
 * puede expresar de forma condicional sobre el mismo campo de un único DTO — esa regla (y
 * el largo mínimo/máximo) la aplica {@code UserService#create}, donde sí se sabe si es alta
 * o edición.
 */
@Data
public class UserRequest {
    // Máximo alineado a users.name VARCHAR(100) — sin este tope, un nombre más largo pasa
    // la validación pero truena al guardar con un error crudo de la base de datos.
    @NotBlank
    @Size(max = 100, message = "El nombre no puede tener más de 100 caracteres")
    private String name;

    // Máximo alineado a users.email VARCHAR(150), mismo motivo que arriba.
    @Email
    @NotBlank
    @Size(max = 150, message = "El correo no puede tener más de 150 caracteres")
    private String email;

    private String password;

    @NotNull
    private Long roleId;
    private Boolean isActive;
    // solo aplica si quien crea/edita es SUPER_ADMIN; para el resto se ignora y se usa su propia tienda
    private Long tiendaId;
}
