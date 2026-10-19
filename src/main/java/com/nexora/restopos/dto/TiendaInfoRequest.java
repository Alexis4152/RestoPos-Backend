package com.nexora.restopos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Payload para actualizar los datos fiscales y de contacto de una tienda
 * ({@code PUT /api/tiendas/{id}/info}, accesible al ADMIN de esa tienda o al SUPER_ADMIN).
 * Estos datos alimentan directamente el encabezado/pie del ticket de venta en PDF
 * (razón social, RFC, dirección desglosada, contacto), por lo que se guardan en la
 * entidad {@code TiendaInfo} asociada a la {@code Tienda}, separada de su información
 * básica de cuenta.
 */
@Data
public class TiendaInfoRequest {
    @NotBlank
    @Size(max = 150, message = "El nombre no puede tener más de 150 caracteres")
    private String name; // Tienda.name — se edita desde la misma pantalla

    // Los límites de abajo son los mismos VARCHAR(N) reales de TiendaInfo (ver esa entidad);
    // redesSociales/notasAdicionales son TEXT (sin límite de columna) — su tope es solo de
    // aplicación, igual que products.description en ProductRequest.
    @Size(max = 20, message = "El RFC no puede tener más de 20 caracteres")
    private String rfc;
    @Size(max = 200, message = "La calle no puede tener más de 200 caracteres")
    private String calle;
    @Size(max = 150, message = "La colonia no puede tener más de 150 caracteres")
    private String colonia;
    @Size(max = 10, message = "El código postal no puede tener más de 10 caracteres")
    private String codigoPostal;
    @Size(max = 150, message = "La localidad no puede tener más de 150 caracteres")
    private String localidad;
    @Size(max = 100, message = "El estado no puede tener más de 100 caracteres")
    private String estado;
    @Size(max = 200, message = "La razón social no puede tener más de 200 caracteres")
    private String razonSocial;
    @Size(max = 30, message = "El teléfono no puede tener más de 30 caracteres")
    private String telefono;
    @Size(max = 200, message = "La página web no puede tener más de 200 caracteres")
    private String paginaWeb;
    @Size(max = 500, message = "Las redes sociales no pueden tener más de 500 caracteres")
    private String redesSociales;
    @Size(max = 500, message = "Los otros datos no pueden tener más de 500 caracteres")
    private String notasAdicionales;
    // Opcional: cargo por envío a domicilio de las órdenes para llevar (null = no se cobra).
    @PositiveOrZero(message = "El costo de envío no puede ser negativo")
    private BigDecimal costoEnvioDomicilio;

    // Conteo ciego al cerrar corte (Tienda.blindCashCount). null = apagado.
    private Boolean blindCashCount;

    // Modo de toma de orden por default de la tienda (Tienda.modoToma): RAPIDO o DETALLADO.
    // null = no se cambia.
    private String modoToma;
}
