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

    /** Línea lista para el carrito de la página web (mismo formato que arma el frontend). */
    public record LineaCarrito(Long productoId, String nombre, BigDecimal precio, String imageUrl,
                               List<ModCarrito> mods, String nota, int cantidad) {}

    /** Extra elegido en una línea del carrito (con el nombre de su grupo, como lo muestra el carrito). */
    public record ModCarrito(Long id, String nombre, BigDecimal precioExtra, String grupo) {}

    /**
     * "Volver a pedir": las líneas de un pedido anterior armadas con el menú de HOY (precios
     * actuales). {@code noDisponibles}: lo que ya no se vende o no está disponible ahora;
     * {@code cambiaron}: platillos que se agregaron sin algún extra que ya no existe.
     */
    public record RepetirPedido(List<LineaCarrito> lineas, List<String> noDisponibles, List<String> cambiaron) {}

    /** Perfil del cliente registrado (sin contraseña). */
    public record ClientePerfil(Long id, String nombre, String telefono, String direccion, String email, boolean emailVerificado) {}

    /** Respuesta de registro/login de cliente: el código de sesión va en el header X-Cliente-Token. */
    public record ClienteSesion(String token, ClientePerfil cliente) {}
}
