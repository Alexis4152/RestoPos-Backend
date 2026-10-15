package com.nexora.restopos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** DTOs de las opiniones de consumo en el restaurante (ver OpinionService). */
public final class OpinionDtos {

    private OpinionDtos() {}

    /** Lo que manda el cliente al opinar (desde el QR o desde "Mis pedidos"). */
    @Data
    public static class Request {
        @NotNull(message = "Elige de 1 a 5 estrellas")
        @Min(value = 1, message = "Elige de 1 a 5 estrellas")
        @Max(value = 5, message = "Elige de 1 a 5 estrellas")
        private Integer estrellas;

        // Opcional: atención del mesero.
        @Min(value = 1, message = "La atención se califica de 1 a 5")
        @Max(value = 5, message = "La atención se califica de 1 a 5")
        private Integer meseroEstrellas;

        @Size(max = 500, message = "El comentario no puede tener más de 500 caracteres")
        private String comentario;

        // Opcional: 👍/👎 por platillo de la cuenta.
        @Size(max = 50)
        @Valid
        private List<Platillo> platillos;
    }

    @Data
    public static class Platillo {
        @NotNull
        private Long ordenDetalleId;
        @NotNull
        private Boolean meGusta;
    }

    /** El mesero liga la orden a un cliente registrado (por su correo o teléfono). */
    @Data
    public static class VincularCliente {
        @NotBlank(message = "Escribe el correo o teléfono del cliente")
        @Size(max = 150)
        private String busqueda;
    }

    /** Platillo de la cuenta tal como se le muestra al cliente para opinar. */
    public record PlatilloCuenta(Long ordenDetalleId, String nombre, BigDecimal cantidad) {}

    /** Opinión ya dejada (resumen para mostrarla). */
    public record OpinionHecha(Integer estrellas, Integer meseroEstrellas, String comentario,
                               List<PlatilloVoto> platillos, LocalDateTime fecha, String canal) {}

    public record PlatilloVoto(String nombre, boolean meGusta) {}

    /**
     * Una cuenta de mesa vista por el cliente que va a opinar. {@code estado}: DISPONIBLE (puede
     * opinar), YA_OPINADA, VENCIDA (pasaron los 7 días) o NO_PAGADA.
     */
    public record CuentaParaOpinar(Long cuentaId, String tienda, String mesa, String mesero,
                                   LocalDateTime fecha, BigDecimal total, List<PlatilloCuenta> platillos,
                                   String estado, LocalDateTime opinarHasta, OpinionHecha opinion) {}

    /** Fila de la pantalla "Opiniones" del personal (mesa o pedido web). */
    public record OpinionStaff(String tipo, Long ordenId, LocalDateTime fecha, String lugar, String mesero,
                               Integer estrellas, Integer meseroEstrellas, String comentario,
                               List<PlatilloVoto> platillos, String cliente, String reporte) {}

    public record MeseroResumen(String mesero, double promedio, long opiniones) {}

    public record PlatilloResumen(String nombre, long meGusta, long noMeGusta) {}

    public record Resumen(long total, double promedio, List<MeseroResumen> meseros, List<PlatilloResumen> platillos,
                          List<OpinionStaff> opiniones) {}

    /** Enlace + QR (PNG en base64) para que el cliente opine de una cuenta. */
    public record EnlaceQr(String url, String qrDataUrl) {}
}
