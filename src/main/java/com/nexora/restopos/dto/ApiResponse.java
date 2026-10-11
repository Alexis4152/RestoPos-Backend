package com.nexora.restopos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Envoltura genérica y estable de TODAS las respuestas de la API REST del sistema.
 * Cada endpoint de cada controller devuelve un {@code ApiResponse<T>} con la forma
 * {@code {success, message, data}}, sin importar el tipo de dato que transporte ({@code T}
 * puede ser una entidad, una lista, un {@link PageResponse}, {@code Void}, etc.).
 * <p>
 * Se usa siempre a través de sus métodos de fábrica estáticos ({@link #ok(Object)},
 * {@link #ok(Object, String)}, {@link #error(String)}) en vez de construirse directamente,
 * de modo que el contrato {@code success}/{@code message} quede consistente en toda la app.
 *
 * @param <T> tipo del payload devuelto en {@code data}
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;

    /** Respuesta exitosa sin mensaje adicional (mensaje queda {@code null}). */
    public static <T> ApiResponse<T> ok(T data)                  { return new ApiResponse<>(true,  null, data); }
    /** Respuesta exitosa con un mensaje descriptivo (ej. "Producto creado") para mostrar al usuario. */
    public static <T> ApiResponse<T> ok(T data, String msg)      { return new ApiResponse<>(true,  msg,  data); }
    /** Respuesta de error: {@code success=false}, sin datos, solo el mensaje de error. */
    public static <T> ApiResponse<T> error(String msg)           { return new ApiResponse<>(false, msg,  null); }
}
