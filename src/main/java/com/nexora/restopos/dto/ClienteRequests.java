package com.nexora.restopos.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * Payloads de las cuentas de cliente de la página web (ver ClienteService). Los topes son
 * los mismos VARCHAR(N) de {@link com.nexora.restopos.model.Cliente}.
 */
public final class ClienteRequests {

    private ClienteRequests() {}

    // Teléfono: dígitos con espacios, guiones, paréntesis o "+" opcionales; 10 a 15 dígitos se
    // valida aparte en el service (aquí solo los caracteres permitidos).
    private static final String TELEFONO_REGEX = "^[0-9+()\\-\\s]+$";

    @Data
    public static class Registro {
        @NotBlank(message = "El nombre es obligatorio")
        @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres")
        private String nombre;

        @NotBlank(message = "El teléfono es obligatorio")
        @Size(max = 30, message = "El teléfono no puede tener más de 30 caracteres")
        @Pattern(regexp = TELEFONO_REGEX, message = "El teléfono solo puede tener números, espacios, guiones o +")
        private String telefono;

        @NotBlank(message = "La dirección es obligatoria")
        @Size(min = 5, max = 200, message = "La dirección debe tener entre 5 y 200 caracteres")
        private String direccion;

        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no es válido")
        @Size(max = 150, message = "El correo no puede tener más de 150 caracteres")
        private String email;

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
        private String password;
    }

    @Data
    public static class Login {
        @NotBlank(message = "Escribe tu correo")
        private String email;
        @NotBlank(message = "Escribe tu contraseña")
        private String password;
    }

    @Data
    public static class Perfil {
        @NotBlank(message = "El nombre es obligatorio")
        @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres")
        private String nombre;

        @NotBlank(message = "El teléfono es obligatorio")
        @Size(max = 30, message = "El teléfono no puede tener más de 30 caracteres")
        @Pattern(regexp = TELEFONO_REGEX, message = "El teléfono solo puede tener números, espacios, guiones o +")
        private String telefono;

        @NotBlank(message = "La dirección es obligatoria")
        @Size(min = 5, max = 200, message = "La dirección debe tener entre 5 y 200 caracteres")
        private String direccion;
    }

    @Data
    public static class Olvide {
        @NotBlank(message = "Escribe tu correo")
        @Email(message = "El correo no es válido")
        private String email;
    }

    @Data
    public static class Verificar {
        @NotBlank
        @Size(max = 100)
        private String token;
    }

    @Data
    public static class Restablecer {
        @NotBlank
        @Size(max = 100)
        private String token;
        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
        private String password;
    }

    @Data
    public static class Reporte {
        // BIEN, MAL o FALTANTE
        @NotBlank(message = "Elige cómo llegó tu pedido")
        private String tipo;
        // Productos del pedido afectados (obligatorio si algo faltó).
        @Size(max = 50)
        private java.util.List<Long> productoIds;
        @Size(max = 500, message = "El comentario no puede tener más de 500 caracteres")
        private String comentario;
    }

    @Data
    public static class Resena {
        @NotNull(message = "Elige de 1 a 5 estrellas")
        @Min(value = 1, message = "Elige de 1 a 5 estrellas")
        @Max(value = 5, message = "Elige de 1 a 5 estrellas")
        private Integer estrellas;

        @Size(max = 500, message = "El comentario no puede tener más de 500 caracteres")
        private String comentario;
    }
}
