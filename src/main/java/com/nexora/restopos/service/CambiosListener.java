package com.nexora.restopos.service;

import com.nexora.restopos.model.Mesa;
import com.nexora.restopos.model.Orden;
import com.nexora.restopos.model.OrdenDetalle;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;

/**
 * Listener JPA de {@link Orden}, {@link OrdenDetalle} y {@link Mesa}: cada vez que se guardan,
 * registra el cambio para que {@link LiveEventsService} lo avise en tiempo real al terminar la
 * transacción. Así ningún servicio tiene que acordarse de "publicar": cualquier cambio que
 * pase por JPA llega a las pantallas.
 */
public class CambiosListener {

    @PostPersist
    @PostUpdate
    @PostRemove
    public void cambio(Object entidad) {
        try {
            if (entidad instanceof Orden o) {
                registrarOrden(o);
            } else if (entidad instanceof OrdenDetalle d && d.getOrden() != null) {
                registrarOrden(d.getOrden());
            } else if (entidad instanceof Mesa m) {
                LiveEventsService.registrar(new LiveEventsService.Cambio("mesa",
                        m.getTienda() != null ? m.getTienda().getId() : null, null, null));
            }
        } catch (Exception ignored) {
            // Un aviso en tiempo real nunca debe romper la operación que lo originó.
        }
    }

    private static void registrarOrden(Orden o) {
        LiveEventsService.registrar(new LiveEventsService.Cambio("orden",
                o.getTienda() != null ? o.getTienda().getId() : null,
                o.getId(),
                o.getCliente() != null ? o.getCliente().getId() : null));
    }
}
