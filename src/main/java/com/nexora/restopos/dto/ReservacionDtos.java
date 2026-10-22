package com.nexora.restopos.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/** Peticiones y vistas de las reservaciones de mesa (ver ReservacionService). */
public final class ReservacionDtos {

    private ReservacionDtos() {}

    /**
     * Reservación nueva: desde la página web (cliente) o capturada por el personal. El tope de
     * personas, la anticipación y el horario los valida el service con la configuración de la tienda.
     */
    @Data
    public static class Crear {
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "El nombre no puede tener más de 120 caracteres")
        private String nombre;

        @NotBlank(message = "El teléfono es obligatorio")
        @Size(max = 30, message = "El teléfono no puede tener más de 30 caracteres")
        private String telefono;

        @Email(message = "El correo no es válido")
        @Size(max = 150, message = "El correo no puede tener más de 150 caracteres")
        private String email;

        @NotNull(message = "Elige la fecha y la hora")
        private LocalDateTime fechaHora;

        @NotNull(message = "Indica cuántas personas son")
        @Min(value = 1, message = "Debe ser al menos 1 persona")
        @Max(value = 100, message = "Demasiadas personas para una reservación")
        private Integer personas;

        private Long zonaId;

        @Min(value = 0, message = "Las periqueras no pueden ser negativas")
        @Max(value = 10, message = "Máximo 10 periqueras")
        private Integer periqueras;

        private Boolean accesible;
        private Boolean carriola;

        @Size(max = 60, message = "La ocasión no puede tener más de 60 caracteres")
        private String ocasion;

        @Size(max = 500, message = "Las indicaciones no pueden tener más de 500 caracteres")
        private String notas;

        // Solo personal: mesa asignada al capturarla (queda confirmada de una vez).
        private Long mesaId;
    }

    @Data
    public static class Confirmar {
        private Long mesaId;
    }

    /** "Llegó": mesa en la que se sientan (obligatoria si traen pedido anticipado y no tenían mesa). */
    @Data
    public static class Llegada {
        private Long mesaId;
    }

    /** Cambios del cliente a su reservación: personas y necesidades (no la fecha ni la hora). */
    @Data
    public static class Cambios {
        @NotNull(message = "Indica cuántas personas son")
        @Min(value = 1, message = "Debe ser al menos 1 persona")
        @Max(value = 100, message = "Demasiadas personas para una reservación")
        private Integer personas;

        @Min(value = 0, message = "Las periqueras no pueden ser negativas")
        @Max(value = 10, message = "Máximo 10 periqueras")
        private Integer periqueras;

        private Boolean accesible;
        private Boolean carriola;

        @Size(max = 60, message = "La ocasión no puede tener más de 60 caracteres")
        private String ocasion;

        @Size(max = 500, message = "Las indicaciones no pueden tener más de 500 caracteres")
        private String notas;
    }

    /** "Llegaré un poco más tarde": 15, 30, 45 o 60 minutos (0 = ya llego a tiempo). */
    @Data
    public static class Retraso {
        @NotNull(message = "Indica cuántos minutos")
        private Integer minutos;
    }

    /** Pedido anticipado para la reservación (se manda a cocina cuando llegan). */
    @Data
    public static class Preorden {
        @jakarta.validation.constraints.NotEmpty(message = "Agrega al menos un producto")
        @jakarta.validation.Valid
        private List<PublicPedidoRequest.Item> items;

        @Size(max = 500, message = "Los comentarios no pueden tener más de 500 caracteres")
        private String notas;
    }

    public record PreordenLinea(String nombre, BigDecimal cantidad, BigDecimal importe) {}

    /** Pedido anticipado: EN_ESPERA (aún no llegan) o EN_COCINA (ya llegaron y se mandó). */
    public record PreordenResumen(Long ordenId, List<PreordenLinea> items, BigDecimal total, String estado) {}

    /** Reglas para la pantalla del personal. */
    public record Reglas(boolean todasLasMesas, int cambiosHoras, int llegadaMinutosAntes) {}

    @Data
    public static class Motivo {
        @Size(max = 300, message = "El motivo no puede tener más de 300 caracteres")
        private String motivo;
    }

    public record ZonaOpcion(Long id, String nombre) {}

    /** Reglas para el formulario de la página web (horario, tope de personas, zonas). */
    public record Opciones(int maxPersonas, int anticipacionHoras, int diasMax,
                           LocalTime horaInicio, LocalTime horaFin, List<ZonaOpcion> zonas,
                           int cambiosHoras, boolean retrasoEnabled, int retrasoHoras) {}

    public record Creada(Long id, String token) {}

    /** Lo que ve el cliente de su reservación (sin datos internos). */
    public record Publica(Long id, String token, String nombre, LocalDateTime fechaHora, int personas,
                          String zona, String mesa, int periqueras, boolean accesible, boolean carriola,
                          String ocasion, String notas, String estado, String motivo, boolean puedeCancelar,
                          LocalDateTime createdAt,
                          Integer retrasoMinutos, boolean puedeModificar, LocalDateTime limiteCambios,
                          boolean puedeAvisarRetraso, LocalDateTime limiteRetraso, boolean puedeOrdenar,
                          PreordenResumen preorden) {}

    /** Vista del personal. */
    public record Staff(Long id, String nombre, String telefono, String email, boolean clienteRegistrado,
                        LocalDateTime fechaHora, int personas, Long zonaId, String zona,
                        Long mesaId, String mesa, Integer mesaCapacidad,
                        int periqueras, boolean accesible, boolean carriola, String ocasion, String notas,
                        String estado, String motivo, String origen, String atendidaPor, LocalDateTime createdAt,
                        Integer retrasoMinutos, LocalDateTime modificadaAt, LocalDateTime llegadaDesde,
                        PreordenResumen preorden) {}
}
