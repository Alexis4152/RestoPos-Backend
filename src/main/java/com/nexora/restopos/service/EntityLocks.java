package com.nexora.restopos.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Bloqueo pesimista que además RELEE la fila.
 * <p>
 * Un {@code findByIdForUpdate} (SELECT ... FOR UPDATE) sobre una entidad que ya estaba cargada
 * en la sesión de Hibernate toma el bloqueo pero regresa la instancia que ya tenía, con los
 * datos de ANTES de esperar el bloqueo: dos operaciones simultáneas leían el mismo folio
 * (folio duplicado), el mismo stock (movimientos con el mismo "stock anterior") o el mismo
 * estado de la venta (doble devolución). {@link #fresh} primero bloquea la fila y DESPUÉS la
 * vuelve a leer. (Un {@code refresh(entity, PESSIMISTIC_WRITE)} no sirve: Hibernate lee la
 * fila y luego la bloquea por separado, así que sigue leyendo datos de antes de esperar.)
 * <p>
 * Llamarlo dentro de una transacción, antes de modificar la entidad (el refresh descarta
 * cambios sin guardar).
 */
@Component
@RequiredArgsConstructor
public class EntityLocks {

    private final EntityManager entityManager;

    /** Bloquea la fila de {@code entity} hasta el fin de la transacción y recarga sus datos. */
    public <T> T fresh(T entity) {
        entityManager.lock(entity, LockModeType.PESSIMISTIC_WRITE); // espera a que se libere
        entityManager.refresh(entity); // ya con la fila bloqueada: datos actuales
        return entity;
    }
}
