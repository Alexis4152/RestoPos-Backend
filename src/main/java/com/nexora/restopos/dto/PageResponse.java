package com.nexora.restopos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Envoltura paginada usada como {@code data} dentro de un {@link ApiResponse} en los
 * endpoints de listado que soportan paginación (ej. historial de cortes de caja, ventas).
 * Expone únicamente los campos que el frontend necesita ({@code content, page, size,
 * totalElements, totalPages}), construida siempre mediante {@link #of(Page)} a partir de
 * un {@link Page} de Spring Data.
 *
 * @param <T> tipo de los elementos de la página (ej. {@code CashCut}, {@code Sale})
 */
// Envuelve un Page de Spring Data en una forma simple y estable para el frontend
// (el JSON de PageImpl directo no es un contrato soportado entre versiones de Spring).
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PageResponse<T> {
    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    /**
     * Construye un {@code PageResponse} a partir de un {@link Page} de Spring Data,
     * copiando su contenido y metadatos de paginación a la forma estable expuesta por la API.
     *
     * @param page resultado paginado devuelto por el repositorio/servicio
     * @return el mismo contenido y metadatos, en el DTO de respuesta
     */
    public static <T> PageResponse<T> of(Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }
}
