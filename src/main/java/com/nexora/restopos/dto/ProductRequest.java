package com.nexora.restopos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Payload para crear o actualizar un {@code Product} del catálogo de la tienda del actor
 * ({@code POST}/{@code PUT /api/products/**}, restringido a ADMIN). Para ajustes puntuales
 * de existencias (entradas/salidas de stock) se usa {@link InventoryAdjustRequest} en su
 * propio endpoint, no este DTO.
 */
@Data
public class ProductRequest {
    // max=200: debe coincidir con Product.name (VARCHAR(200)) — sin este tope, un nombre más
    // largo pasa la validación pero truena al guardar con un error crudo de la base de datos.
    @NotBlank
    @Size(max = 200, message = "El nombre no puede tener más de 200 caracteres")
    private String name;

    // products.description es TEXT (sin límite de columna) — este tope es de higiene de la
    // app, no de la base de datos. Opcional.
    @Size(max = 50, message = "La descripción no puede tener más de 50 caracteres")
    private String description;
    // max=100: debe coincidir con Product.barcode (VARCHAR(100)).
    @Size(max = 100, message = "El código de barras no puede tener más de 100 caracteres")
    private String barcode;
    @NotNull @PositiveOrZero
    private BigDecimal price;
    @PositiveOrZero
    private BigDecimal cost;
    // Stock inicial/absoluto del producto (no un delta); los ajustes posteriores de
    // inventario se hacen vía InventoryAdjustRequest, no reenviando este campo.
    @PositiveOrZero
    private Integer stock;
    // Umbral usado para marcar el producto como "stock bajo" en el buscador y en el
    // widget del Dashboard.
    @PositiveOrZero
    private Integer minStock;
    // max=20: debe coincidir con Product.unit (VARCHAR(20)).
    @Size(max = 20, message = "La unidad no puede tener más de 20 caracteres")
    private String unit;
    private String imageUrl;
    private Boolean disponible;
    // Sin esto, un categoryId nulo llega hasta CategoryService.findById(null, actor) y truena
    // con un error crudo en vez de un 400 legible.
    @NotNull(message = "Selecciona una categoría")
    private Long categoryId;
}
