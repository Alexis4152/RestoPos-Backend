package com.nexora.restopos.config;

import com.nexora.restopos.model.Tienda;
import com.nexora.restopos.repository.TiendaRepository;
import com.nexora.restopos.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Las tiendas creadas antes de que existiera el rol REPARTIDOR no lo tienen (las nuevas lo
 * reciben en RoleService#seedDefaultRolesForTienda). Idempotente: corre en cada arranque,
 * después de que RoleDataInitializer ya ajustó el CHECK de secciones para incluir DELIVERY.
 */
@Component
@Order(20)
@RequiredArgsConstructor
public class RepartidorRoleBackfill implements CommandLineRunner {

    private final TiendaRepository tiendaRepository;
    private final RoleService roleService;

    @Override
    @Transactional
    public void run(String... args) {
        for (Tienda t : tiendaRepository.findAllByOrderByNameAsc()) {
            if (Boolean.TRUE.equals(t.getIsActive())) roleService.ensureRepartidorRole(t);
        }
    }
}
