package com.nexora.restopos.dto;

import com.nexora.restopos.model.Mesa;
import com.nexora.restopos.model.OrdenDetalle;
import com.nexora.restopos.model.OrdenEstado;
import com.nexora.restopos.model.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Una orden activa tal como la ve la pantalla de Cocina: solo los datos que un cocinero
 * necesita (mesa, mesero, cuándo se abrió, y sus productos — ya sin los cancelados y ya
 * ordenados por cuándo se pidieron) — ver {@link com.nexora.restopos.service.OrdenService#findActiveForKitchen}.
 * <p>
 * Deliberadamente no es la {@link com.nexora.restopos.model.Orden} completa: su colección
 * {@code items} tiene {@code orphanRemoval=true}, así que filtrarla/ordenarla in-place
 * arriesgaría que Hibernate interprete eso como líneas huérfanas y las borre de la base de
 * datos. Esta vista es una copia nueva, nunca la lista administrada por el entity.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class KitchenOrdenView {
    private Long id;
    private Mesa mesa;
    private User mesero;
    private LocalDateTime openedAt;
    private OrdenEstado estado;
    private List<OrdenDetalle> items;
}
