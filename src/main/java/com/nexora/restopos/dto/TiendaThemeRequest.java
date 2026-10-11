package com.nexora.restopos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Payload para cambiar el color de marca de una tienda
 * ({@code PUT /api/tiendas/{id}/theme}). A diferencia del resto de endpoints bajo
 * {@code /api/tiendas}, este lo puede usar tanto el SUPER_ADMIN como el ADMIN de esa
 * misma tienda (para personalizar su propio look), nunca el ADMIN de otra tienda. El
 * valor se guarda en {@code Tienda.primaryColor} y el frontend lo usa para theming.
 */
@Data
public class TiendaThemeRequest {
    // Color hexadecimal de 6 dígitos con "#" (ej. "#155dea"); formato validado por el Pattern.
    @NotBlank
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "El color debe ser un hex válido, ej. #155dea")
    private String primaryColor;
}
