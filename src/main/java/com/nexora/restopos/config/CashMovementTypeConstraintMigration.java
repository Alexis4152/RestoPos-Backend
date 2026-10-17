package com.nexora.restopos.config;

import com.nexora.restopos.model.CashMovementType;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Mantiene al día la restricción {@code cash_movements_type_check}. Hibernate la crea con
 * los valores de {@link CashMovementType} al crear la tabla, pero {@code ddl-auto=update}
 * nunca la actualiza cuando el enum crece: sin esto, insertar un tipo nuevo (p. ej.
 * {@code SHORTAGE_PAYMENT}) truena con una violación de la restricción. La vuelve a crear
 * en cada arranque con todos los valores actuales del enum — idempotente.
 */
@Component
@Order(30)
@RequiredArgsConstructor
@Slf4j
public class CashMovementTypeConstraintMigration implements CommandLineRunner {

    private final EntityManager entityManager;

    @Override
    @Transactional
    public void run(String... args) {
        String values = Arrays.stream(CashMovementType.values())
                .map(t -> "'" + t.name() + "'")
                .collect(Collectors.joining(", "));
        entityManager.createNativeQuery("ALTER TABLE cash_movements DROP CONSTRAINT IF EXISTS cash_movements_type_check").executeUpdate();
        entityManager.createNativeQuery("ALTER TABLE cash_movements ADD CONSTRAINT cash_movements_type_check CHECK (type IN (" + values + "))").executeUpdate();
        log.info("Restricción cash_movements_type_check actualizada: {}", values);
    }
}
