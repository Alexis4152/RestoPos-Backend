package com.nexora.restopos.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Vistas de solo lectura que expone la página web pública de una tienda (ver
 * {@link com.nexora.restopos.controller.PublicController}). Deliberadamente NO son las
 * entidades: así nunca se filtra al público costo, stock, usuarios ni nada interno.
 */
public final class PublicSiteDtos {

    private PublicSiteDtos() {}

    /** Portada del sitio: datos de la tienda y qué opciones de pedido/pago tiene habilitadas. */
    public record Tienda(
            Long tiendaId,
            String nombre,
            String logoPath,
            String primaryColor,
            String telefono,
            String direccion,
            String mensaje,
            boolean mostrarMesas,
            boolean recoger,
            boolean domicilio,
            BigDecimal costoEnvio,
            boolean pagoEfectivo,
            boolean pagoTarjeta,
            boolean pagoTransferencia,
            String transferenciaBanco,
            String transferenciaTitular,
            String transferenciaClabe,
            boolean reservaciones
    ) {}

    public record Opcion(Long id, String nombre, BigDecimal precioExtra) {}

    public record Grupo(Long id, String nombre, String tipo, boolean obligatorio, List<Opcion> opciones) {}

    public record Producto(Long id, String nombre, String descripcion, BigDecimal precio, String imageUrl, List<Grupo> grupos) {}

    public record Categoria(Long id, String nombre, String descripcion, List<Producto> productos) {}

    /** Mesa vista desde afuera: solo número, capacidad y si está libre u ocupada. */
    public record Mesa(String numero, Integer capacidad, String estado) {}

    public record Zona(String nombre, int libres, int ocupadas, List<Mesa> mesas) {}

    /**
     * Respuesta al crear un pedido. {@code token} es el código de seguimiento con el que el
     * cliente consulta después en qué va (única vez que se le entrega).
     */
    public record PedidoCreado(Long id, BigDecimal total, String token) {}

    /** Perfil del cliente registrado (sin contraseña). */
    public record ClientePerfil(Long id, String nombre, String telefono, String direccion, String email, boolean emailVerificado) {}

    /** Respuesta de registro/login de cliente: el código de sesión va en el header X-Cliente-Token. */
    public record ClienteSesion(String token, ClientePerfil cliente) {}
}
