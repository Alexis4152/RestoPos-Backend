package com.nexora.restopos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Pedido hecho por un cliente desde la página web pública de la tienda
 * ({@code POST /api/public/r/{slug}/pedidos}, sin sesión) — ver OrdenService#crearPedidoWeb.
 * Los topes de tamaño/cantidad limitan lo que un desconocido puede mandar de un jalón.
 */
@Data
public class PublicPedidoRequest {
    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre no puede tener más de 100 caracteres")
    private String clienteNombre;

    @NotBlank(message = "El teléfono es obligatorio")
    @Size(max = 30, message = "El teléfono no puede tener más de 30 caracteres")
    private String clienteTelefono;

    // RECOGER o DOMICILIO
    @NotBlank(message = "Elige si es para recoger o a domicilio")
    private String tipoEntrega;

    // Obligatoria si tipoEntrega = DOMICILIO (se valida en el service).
    @Size(max = 200, message = "La dirección no puede tener más de 200 caracteres")
    private String direccion;

    // EFECTIVO, TARJETA o TRANSFERENCIA
    @NotBlank(message = "Elige una forma de pago")
    private String metodoPago;

    @Size(max = 500, message = "Las notas no pueden tener más de 500 caracteres")
    private String notas;

    @NotEmpty(message = "Tu pedido está vacío")
    @Size(max = 50, message = "Demasiados productos en un solo pedido")
    @Valid
    private List<Item> items;

    @Data
    public static class Item {
        @NotNull
        private Long productoId;
        @NotNull @Positive @Max(value = 99, message = "Máximo 99 piezas por producto")
        private Integer cantidad;
        @Size(max = 200, message = "La nota de un producto no puede tener más de 200 caracteres")
        private String observaciones;
        private List<Long> modificadorOpcionIds;
    }
}
