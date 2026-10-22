package com.nexora.restopos.config;

import com.nexora.restopos.model.AppSection;
import com.nexora.restopos.model.Role;
import com.nexora.restopos.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * {@link CommandLineRunner} que siembra los roles por defecto del sistema (ADMIN, CASHIER,
 * SELLER) la primera vez que arranca la aplicación con una base de datos sin roles, y deja
 * asignado el rol ADMIN a la cuenta administradora inicial ({@code admin@restopos.com}).
 *
 * <p>{@code @Order(1)} garantiza que corra antes que {@link TenantDataInitializer}
 * ({@code @Order(10)}), ya que este último migra roles compartidos entre tiendas y necesita
 * que los roles base ya existan. Sin esta anotación, un {@link CommandLineRunner} sin orden
 * explícito se trata como {@code Ordered.LOWEST_PRECEDENCE} y correría después, al revés de
 * lo requerido.</p>
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class RoleDataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Orquesta el sembrado inicial: primero crea los roles base si la tabla está vacía, luego
     * asegura que la cuenta admin por defecto tenga un rol asignado.
     */
    @Override
    @Transactional
    public void run(String... args) {
        patchRoleSectionsCheckConstraint();
        seedDefaultRoles();
        assignDefaultAdminRole();
        backfillSupervisorRole();
        backfillSystemRoleSections();
    }

    // role_sections.section tiene un CHECK constraint hecho a mano en init.sql; Hibernate
    // (ddl-auto=update) agrega columnas nuevas pero nunca toca CHECK constraints existentes,
    // así que cada vez que se agrega un valor nuevo a AppSection hay que relajar el constraint
    // aquí para poder insertar filas con ese valor. Se ejecuta en cada arranque: DROP IF EXISTS
    // + ADD es idempotente (no falla si no existía) y siempre deja el constraint alineado con
    // el enum actual.
    private void patchRoleSectionsCheckConstraint() {
        jdbcTemplate.execute("ALTER TABLE role_sections DROP CONSTRAINT IF EXISTS role_sections_section_check");
        StringBuilder allowed = new StringBuilder();
        for (AppSection s : AppSection.values()) {
            if (allowed.length() > 0) allowed.append(',');
            allowed.append('\'').append(s.name()).append('\'');
        }
        jdbcTemplate.execute("ALTER TABLE role_sections ADD CONSTRAINT role_sections_section_check " +
                "CHECK (section IN (" + allowed + "))");
    }

    /**
     * Crea los roles ADMIN (acceso total, marcado {@code isSystem}), CASHIER y SELLER (todas
     * las secciones excepto USERS y ROLES) si todavía no existe ningún rol en la base de
     * datos. No hace nada si ya hay al menos un rol sembrado, para no duplicar en arranques
     * posteriores.
     */
    private void seedDefaultRoles() {
        if (roleRepository.count() > 0) return;

        // Sin CASH_CUTS a propósito: registrar un pago (PagoController#registrar) exige esa
        // sección, y el mesero no cobra — genera e imprime la cuenta y el cliente paga en caja
        // (rol CAJERO). Si en una tienda chica el mesero sí cobra, el admin le agrega la sección.
        Set<AppSection> meseroSections = EnumSet.of(AppSection.DASHBOARD, AppSection.TABLES, AppSection.ORDERS);

        // global, sin tienda: usuario de plataforma con visibilidad de todas las tiendas
        Role superAdmin = Role.builder()
                .name("SUPER_ADMIN")
                .description("Super administrador — plataforma completa")
                .isSystem(true)
                .sections(EnumSet.allOf(AppSection.class))
                .build();
        // global, sin tienda: usuario de plataforma a cargo de un subconjunto de tiendas
        Role supervisor = Role.builder()
                .name("SUPERVISOR")
                .description("Supervisor — administra las tiendas que tiene a su cargo")
                .isSystem(true)
                .sections(EnumSet.allOf(AppSection.class))
                .build();
        Role admin = Role.builder()
                .name("ADMIN")
                .description("Administrador — acceso total a su tienda")
                .isSystem(true)
                .sections(EnumSet.allOf(AppSection.class))
                .build();
        Role mesero = Role.builder()
                .name("MESERO")
                .description("Mesero — mesas y toma de órdenes")
                .sections(meseroSections)
                .build();

        roleRepository.saveAll(List.of(superAdmin, supervisor, admin, mesero));
        log.info("Roles sembrados: SUPER_ADMIN, SUPERVISOR, ADMIN, MESERO");
    }

    /**
     * Crea el rol de plataforma SUPERVISOR si todavía no existe (caso de una base de datos
     * que ya tenía roles sembrados de antes de que este rol existiera). No hace nada si ya
     * hay un SUPERVISOR (sin tienda) en la base de datos.
     */
    private void backfillSupervisorRole() {
        if (roleRepository.findFirstByNameAndTiendaIsNullOrderById("SUPERVISOR").isPresent()) return;
        roleRepository.save(Role.builder()
                .name("SUPERVISOR")
                .description("Supervisor — administra las tiendas que tiene a su cargo")
                .isSystem(true)
                .sections(EnumSet.allOf(AppSection.class))
                .build());
        log.info("Rol SUPERVISOR sembrado (backfill)");
    }

    // Cuando se agrega un AppSection nuevo (ej. SALES), los roles de sistema ya sembrados en
    // bases de datos existentes se quedan sin ese permiso porque su Set<AppSection> ya estaba
    // persistido antes de que existiera. Backfill idempotente: a todo rol isSystem=true le
    // asegura tener TODAS las secciones actuales del enum (que es justo lo que significa ser
    // un rol de sistema con acceso total).
    private void backfillSystemRoleSections() {
        List<Role> systemRoles = roleRepository.findAllByIsSystemTrue();
        int updated = 0;
        for (Role r : systemRoles) {
            if (!r.getSections().containsAll(EnumSet.allOf(AppSection.class))) {
                r.getSections().addAll(EnumSet.allOf(AppSection.class));
                roleRepository.save(r);
                updated++;
            }
        }
        if (updated > 0) {
            log.info("Secciones de {} rol(es) de sistema actualizadas (backfill)", updated);
        }
    }

    /**
     * Asigna el rol ADMIN a la cuenta administradora sembrada por {@code init.sql} en caso de
     * que todavía no tenga {@code role_id}. Es idempotente: solo actualiza filas con
     * {@code role_id IS NULL}, así que en arranques posteriores no hace nada.
     */
    // init.sql da de alta admin@restopos.com sin role_id (la tabla roles la maneja Hibernate,
    // no ese script) — este paso idempotente le asigna ADMIN si todavía no tiene rol.
    // findFirstByNameOrderById (no findByName): una vez que cada tienda tiene su propio
    // rol "ADMIN", puede haber más de uno y findByName tronaría por resultado ambiguo.
    private void assignDefaultAdminRole() {
        Role admin = roleRepository.findFirstByNameOrderById("ADMIN").orElse(null);
        if (admin == null) return;
        int updated = jdbcTemplate.update(
                "UPDATE users SET role_id = ? WHERE email = 'admin@restopos.com' AND role_id IS NULL",
                admin.getId());
        if (updated > 0) {
            log.info("Rol ADMIN asignado a admin@restopos.com");
        }
    }
}
