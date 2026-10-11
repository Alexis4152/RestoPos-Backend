package com.nexora.restopos.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Payload para ajustar manualmente el stock de un producto
 * ({@code POST /api/products/{id}/adjust-stock}). A diferencia del resto de operaciones
 * de inventario (crear/editar producto), este endpoint no requiere rol ADMIN: basta con
 * tener acceso a la sección de Inventario ({@link com.nexora.restopos.model.AppSection#INVENTORY}).
 */
@Data
public class InventoryAdjustRequest {
    // Cantidad a sumar (positiva) o restar (negativa) al stock actual del producto;
    // no es el nuevo total, sino el delta a aplicar. Un valor negativo (quitar piezas)
    // solo lo puede aplicar un ADMIN; el servicio lo rechaza para otros roles.
    @NotNull
    private Integer quantity;
    // Motivo del ajuste (opcional), útil para auditar mermas, conteos físicos, etc. Tope
    // alineado a InventoryMovement.reason (VARCHAR(255)) — sin esto, un motivo más largo
    // pasa la validación pero truena al guardar con un error crudo de la base de datos.
    @Size(max = 255, message = "El motivo no puede tener más de 255 caracteres")
    private String reason;
}
