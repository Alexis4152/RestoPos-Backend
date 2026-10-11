package com.nexora.restopos.config;

import com.nexora.restopos.model.Role;
import com.nexora.restopos.model.Tienda;
import com.nexora.restopos.model.Zona;
import com.nexora.restopos.repository.RoleRepository;
import com.nexora.restopos.repository.TiendaRepository;
import com.nexora.restopos.repository.ZonaRepository;
import com.nexora.restopos.service.RoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * {@link CommandLineRunner} de migración que introduce el multi-tenant ("Tienda") sobre una
 * base de datos que puede traer datos previos a ese concepto: crea la tienda por defecto si
 * hace falta, rellena {@code tienda_id} en las tablas que antes no lo tenían, y separa los
 * roles que originalmente eran compartidos entre todas las tiendas en copias independientes
 * por tienda, y asigna una zona a las mesas que quedaron sin ninguna (ver {@link #backfillZonas()},
 * agregado después de que ya existían mesas). Es idempotente en sus pasos: en arranques
 * posteriores, una vez migrados los datos, no vuelve a hacer nada.
 *
 * <p>{@code @Order(10)} garantiza que corra después de {@link RoleDataInitializer} (sin orden
 * explícito), de modo que los roles base y el rol SUPER_ADMIN ya existan antes de intentar la
 * migración de roles compartidos.</p>
 */
// Runs after RoleDataInitializer so SUPER_ADMIN and legacy-role migration are already in place.
@Component
@Order(10)
@RequiredArgsConstructor
@Slf4j
public class TenantDataInitializer implements CommandLineRunner {

    private static final List<String> TABLES_TO_BACKFILL = List.of("products", "categories", "mesas", "ordenes", "cash_cuts");
    // Roles/usuarios de plataforma: deben permanecer siempre sin tienda (tienda_id NULL),
    // nunca migrarse a "propiedad" de la primera tienda que exista.
    private static final Set<String> PLATFORM_ROLE_NAMES = Set.of("SUPER_ADMIN", "SUPERVISOR");

    private final TiendaRepository tiendaRepository;
    private final RoleRepository roleRepository;
    private final ZonaRepository zonaRepository;
    private final RoleService roleService;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Orquesta la migración completa en orden: asegura que exista una tienda por defecto,
     * rellena {@code tienda_id} en registros huérfanos de esa tienda, y migra los roles
     * compartidos a roles propios por tienda.
     */
    @Override
    @Transactional
    public void run(String... args) {
        Tienda defaultTienda = ensureDefaultTienda();
        backfillTiendaId(defaultTienda);
        migrateSharedRolesToTiendas();
        backfillZonas();
    }

    /**
     * Devuelve la primera tienda existente (por nombre) o, si todavía no hay ninguna, crea
     * "Tienda Principal" activa para servir como destino del backfill de datos legados.
     */
    private Tienda ensureDefaultTienda() {
        if (tiendaRepository.count() > 0) {
            return tiendaRepository.findAllByOrderByNameAsc().get(0);
        }
        Tienda tienda = new Tienda();
        tienda.setName("Tienda Principal");
        tienda.setIsActive(true);
        tienda = tiendaRepository.save(tienda);
        log.info("Tienda por defecto creada: {}", tienda.getName());
        return tienda;
    }

    /**
     * Asigna {@code defaultTienda} a todos los registros con {@code tienda_id} nulo en las
     * tablas listadas en {@link #TABLES_TO_BACKFILL}, y a los usuarios sin tienda que no
     * tengan un rol de plataforma ({@link #PLATFORM_ROLE_NAMES}) — esas cuentas deben
     * permanecer sin tienda, ya que operan a nivel plataforma.
     */
    private void backfillTiendaId(Tienda defaultTienda) {
        for (String table : TABLES_TO_BACKFILL) {
            int updated = jdbcTemplate.update(
                    "UPDATE " + table + " SET tienda_id = ? WHERE tienda_id IS NULL", defaultTienda.getId());
            if (updated > 0) {
                log.info("Migrados {} registros de '{}' a la tienda por defecto", updated, table);
            }
        }
        // Users are handled separately: platform-role accounts must stay tienda-less,
        // everyone else without a tienda yet is assigned to the default one.
        int updatedUsers = jdbcTemplate.update(
                "UPDATE users SET tienda_id = ? WHERE tienda_id IS NULL " +
                        "AND role_id NOT IN (SELECT id FROM roles WHERE name IN ('SUPER_ADMIN', 'SUPERVISOR'))",
                defaultTienda.getId());
        if (updatedUsers > 0) {
            log.info("Migrados {} usuarios a la tienda por defecto", updatedUsers);
        }
    }

    /**
     * Migración para el módulo de zonas (agregado después de que ya existían mesas sin
     * zona): por cada tienda con mesas huérfanas ({@code zona_id IS NULL}), crea "Zona
     * Principal" si esa tienda todavía no tiene ninguna zona activa, y les asigna esa zona.
     * Idempotente — en arranques posteriores, sin mesas huérfanas, no hace nada.
     */
    private void backfillZonas() {
        for (Tienda tienda : tiendaRepository.findAllByOrderByNameAsc()) {
            Integer huerfanas = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM mesas WHERE tienda_id = ? AND zona_id IS NULL", Integer.class, tienda.getId());
            if (huerfanas == null || huerfanas == 0) continue;

            Zona zona = zonaRepository.findFirstByTiendaIdAndIsActiveTrueOrderByNombreAsc(tienda.getId())
                    .orElseGet(() -> {
                        Zona z = new Zona();
                        z.setNombre("Zona Principal");
                        z.setTienda(tienda);
                        Zona saved = zonaRepository.save(z);
                        log.info("Zona 'Zona Principal' creada para la tienda {}", tienda.getName());
                        return saved;
                    });
            int updated = jdbcTemplate.update(
                    "UPDATE mesas SET zona_id = ? WHERE tienda_id = ? AND zona_id IS NULL", zona.getId(), tienda.getId());
            log.info("Migradas {} mesas de la tienda {} a la zona '{}'", updated, tienda.getName(), zona.getNombre());
        }
    }

    /**
     * Migra los roles que todavía no pertenecen a ninguna tienda (creados antes de que
     * existiera el multi-tienda) a un modelo de un rol independiente por tienda. Los roles
     * de plataforma ({@link #PLATFORM_ROLE_NAMES}) se excluyen a propósito porque son
     * globales por diseño.
     */
    // Un solo evento, la primera vez que hay más de una tienda: los roles ADMIN/CASHIER/SELLER
    // (y cualquier otro que se haya creado antes de que existiera el multi-tienda) eran
    // compartidos por todas las tiendas. Esto le da la propiedad de esos roles a la primera
    // tienda tal cual estaban, y le clona una copia independiente a cada tienda adicional,
    // reasignando a sus usuarios — así editar un rol en una tienda ya no afecta a las demás.
    private void migrateSharedRolesToTiendas() {
        List<Tienda> tiendas = tiendaRepository.findAllByOrderByNameAsc();
        if (tiendas.isEmpty()) return;

        List<Role> tiendaLessRoles = roleRepository.findAllByTiendaIsNull();
        tiendaLessRoles.removeIf(r -> PLATFORM_ROLE_NAMES.contains(r.getName())); // esos son globales, se quedan sin tienda

        if (tiendaLessRoles.isEmpty()) return; // ya migrado, o no había nada que migrar

        Tienda owner = tiendas.get(0);
        for (Role r : tiendaLessRoles) {
            r.setTienda(owner);
        }
        roleRepository.saveAll(tiendaLessRoles);
        log.info("Roles compartidos ({}) asignados a la tienda '{}'",
                tiendaLessRoles.size(), owner.getName());

        for (Tienda t : tiendas) {
            if (t.getId().equals(owner.getId())) continue;
            for (Role template : tiendaLessRoles) {
                if (roleRepository.existsByNameAndTiendaId(template.getName(), t.getId())) continue;
                Role clone = roleService.createSeedRole(template.getName(), template.getDescription(),
                        Boolean.TRUE.equals(template.getIsSystem()), template.getSections(), t);
                int updated = jdbcTemplate.update(
                        "UPDATE users SET role_id = ? WHERE tienda_id = ? AND role_id = ?",
                        clone.getId(), t.getId(), template.getId());
                if (updated > 0) {
                    log.info("{} usuario(s) de '{}' reasignados a su propio rol '{}'",
                            updated, t.getName(), template.getName());
                }
            }
        }
    }
}
