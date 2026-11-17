package com.nexora.restopos.service;

import com.nexora.restopos.dto.UserRequest;
import com.nexora.restopos.exception.FieldConflictException;
import com.nexora.restopos.model.CashCutStatus;
import com.nexora.restopos.model.Role;
import com.nexora.restopos.model.Tienda;
import com.nexora.restopos.model.User;
import com.nexora.restopos.repository.CashCutRepository;
import com.nexora.restopos.repository.RoleRepository;
import com.nexora.restopos.repository.TiendaRepository;
import com.nexora.restopos.repository.UserRepository;
import com.nexora.restopos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * CRUD de usuarios del sistema, con aislamiento por tienda (multi-tenancy) y control de
 * a qué tienda puede asignarse un usuario según quién lo esté dando de alta.
 *
 * <p>Las contraseñas siempre se guardan cifradas vía {@link PasswordEncoder}, nunca en
 * texto plano. La asignación de tienda a un usuario nuevo depende de quién lo crea (ver
 * {@link #resolveTiendaForWrite(UserRequest, User, Role)}): un SUPER_ADMIN puede asignar
 * cualquier tienda; cualquier otro actor solo puede dar de alta usuarios dentro de su
 * propia tienda (o la que esté actuando, si es SUPERVISOR). Los roles de plataforma
 * (SUPER_ADMIN/SUPERVISOR) nunca tienen tienda propia.</p>
 *
 * <p>Registrar un SUPERVISOR (solo SUPER_ADMIN) incluye, en el mismo alta/edición, qué
 * tiendas va a administrar ({@link UserRequest#getSupervisedTiendaIds()}) y el máximo de
 * tiendas de su plan contratado ({@link UserRequest#getMaxTiendas()}).</p>
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleService roleService;
    private final RoleRepository roleRepository;
    private final TiendaRepository tiendaRepository;
    private final CashCutRepository cashCutRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantScope tenantScope;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    // Misma property que usa el link de login del correo de contraseña temporal — en local
    // cae en localhost:5173 por default; en prod se fija con la variable de entorno
    // APP_FRONTEND_URL al dominio real donde quede desplegado el frontend.
    @Value("${app.frontend.url}")
    private String frontendUrl;

    // BETWEEN siempre necesita las dos fechas: Postgres no logra inferir el tipo de un
    // parámetro timestamp nulo (mismo caso que en SaleService/CashCutService.findAll).
    private static final LocalDateTime MIN_DATE = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final LocalDateTime MAX_DATE = LocalDateTime.of(2100, 1, 1, 0, 0);

    // Sin caracteres ambiguos (0/O, 1/l/I) para que quien la teclee a mano desde el correo
    // no se equivoque adivinando cuál es cuál.
    private static final String TEMP_PASSWORD_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final int TEMP_PASSWORD_LENGTH = 10;

    // Jerarquía de roles de plataforma/tienda: un actor solo puede crear o administrar
    // cuentas de un rango estrictamente menor al suyo (número más bajo = más poder). Los
    // roles no listados aquí (MESERO, cualquier rol personalizado) caen todos en
    // UNRANKED_ROLE, por debajo de ADMIN.
    private static final Map<String, Integer> ROLE_RANK = Map.of(
            "SUPER_ADMIN", 0,
            "SUPERVISOR", 1,
            "ADMIN", 2
    );
    private static final int UNRANKED_ROLE = 3;

    private int rankOf(Role role) {
        return role != null ? ROLE_RANK.getOrDefault(role.getName(), UNRANKED_ROLE) : UNRANKED_ROLE;
    }

    // Evita que, por ejemplo, un ADMIN dé de alta a otro ADMIN, o que un SUPERVISOR asigne
    // el rol SUPERVISOR/SUPER_ADMIN — solo se puede asignar un rol de rango estrictamente
    // inferior al propio.
    private void assertCanAssignRole(Role target, User actor) {
        if (rankOf(target) <= rankOf(actor.getRole())) {
            throw new AccessDeniedException("No puedes asignar un rol igual o superior al tuyo");
        }
    }

    // Igual que assertCanAssignRole pero para administrar (editar/desactivar) una cuenta ya
    // existente; un usuario siempre puede administrarse a sí mismo (ej. cambiar su propia
    // contraseña vía este mismo endpoint de edición).
    private void assertCanManage(User target, User actor) {
        if (target.getId().equals(actor.getId())) return;
        if (rankOf(target.getRole()) <= rankOf(actor.getRole())) {
            throw new AccessDeniedException("No puedes administrar una cuenta de tu mismo nivel o superior");
        }
    }

    /**
     * Busca usuarios aplicando filtros combinables, acotado a la tienda del actor.
     *
     * <p>Los usuarios con rol de plataforma (SUPER_ADMIN, SUPERVISOR) no tienen tienda, así
     * que nunca calzarían con el filtro normal de "tienda que esté actuando" — por eso, si
     * un SUPER_ADMIN filtra explícitamente por un rol de plataforma, se busca sin filtro de
     * tienda (para que pueda volver a encontrar al Supervisor que acaba de registrar). Y
     * viendo la lista de una tienda concreta, SUPER_ADMIN ve también al Supervisor asignado
     * a ESA tienda. Nada de esto aplica a Supervisor/Admin/Mesero: ellos solo ven usuarios
     * de su propia tienda.</p>
     *
     * @param from fecha de alta mínima (inclusiva); null = sin límite inferior
     * @param to fecha de alta máxima (inclusiva); null = sin límite superior
     * @param name filtro por nombre (parcial), o null para no filtrar
     * @param email filtro por correo (parcial), o null para no filtrar
     * @param roleId filtro por id de rol, o null para no filtrar
     * @param isActive filtro por estado activo/inactivo, o null para no filtrar
     * @param actor usuario que realiza la consulta; acota el resultado a su tienda
     * @return usuarios que cumplen los filtros dentro del alcance del actor
     */
    public List<User> findAll(LocalDateTime from, LocalDateTime to, String name, String email,
                               Long roleId, Boolean isActive, User actor) {
        LocalDateTime effectiveFrom = from != null ? from : MIN_DATE;
        LocalDateTime effectiveTo = to != null ? to : MAX_DATE;
        Long scope = tenantScope.isSuperAdmin(actor) && isPlatformRole(roleId) ? null : tenantScope.scopeId(actor);

        Long tiendaSupervisorId = null;
        if (scope != null && tenantScope.isSuperAdmin(actor) && (roleId == null || isRoleNamed(roleId, "SUPERVISOR"))) {
            tiendaSupervisorId = tiendaRepository.findById(scope)
                    .map(Tienda::getSupervisor).map(User::getId).orElse(null);
        }
        return userRepository.search(scope, effectiveFrom, effectiveTo, name, email, roleId, isActive, tiendaSupervisorId);
    }

    /** {@code true} si el id de rol dado corresponde a un rol de plataforma (sin tienda);
     *  {@code false} (nunca truena) si el id es null o no existe. */
    private boolean isPlatformRole(Long roleId) {
        if (roleId == null) return false;
        try {
            return roleService.findById(roleId).getTienda() == null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** {@code true} si el id de rol dado es exactamente el rol llamado {@code name}. */
    private boolean isRoleNamed(Long roleId, String name) {
        if (roleId == null) return false;
        try {
            return name.equals(roleService.findById(roleId).getName());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** {@code true} si el rol dado es uno "de plataforma" (SUPER_ADMIN o SUPERVISOR): sin
     *  tienda propia por diseño, ver {@link #resolveTiendaForWrite}. */
    private boolean isPlatformRole(Role role) {
        return role != null && role.getTienda() == null
                && ("SUPER_ADMIN".equals(role.getName()) || "SUPERVISOR".equals(role.getName()));
    }

    private boolean isSupervisorRole(Role role) {
        return isPlatformRole(role) && "SUPERVISOR".equals(role.getName());
    }

    /**
     * Busca un usuario por id sin validar a qué tienda pertenece. Uso interno; para
     * flujos con control de acceso usar {@link #findById(Long, User)}.
     *
     * @param id id del usuario
     * @return el usuario encontrado
     * @throws IllegalArgumentException si no existe
     */
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + id));
    }

    /**
     * Busca un usuario por id validando que pertenezca a la tienda del actor. SUPER_ADMIN
     * puede ver CUALQUIER usuario por id, sin importar en qué tienda esté actuando — incluye
     * a otros usuarios de plataforma (SUPERVISOR) que, al no tener tienda, nunca calzarían
     * con el filtro normal.
     *
     * @param id id del usuario
     * @param actor usuario que realiza la consulta
     * @return el usuario encontrado, perteneciente al alcance del actor
     * @throws IllegalArgumentException si no existe o no pertenece a la tienda del actor
     */
    public User findById(Long id, User actor) {
        User u = findById(id);
        if (tenantScope.isSuperAdmin(actor)) return u;
        Long scope = tenantScope.scopeId(actor);
        if (scope != null && (u.getTienda() == null || !scope.equals(u.getTienda().getId()))) {
            throw new IllegalArgumentException("Usuario no encontrado: " + id);
        }
        return u;
    }

    /**
     * Indica si dar de alta este correo terminaría reactivando un usuario existente en vez de
     * crear uno nuevo (ver {@link #create}) — usado por el controller solo para variar el
     * mensaje de éxito, no cambia ninguna regla de negocio.
     *
     * @param email correo a comprobar
     * @return {@code true} si ese correo pertenece a un usuario actualmente desactivado
     */
    public boolean isReactivatableEmail(String email) {
        return userRepository.findByEmail(email).map(u -> !Boolean.TRUE.equals(u.getIsActive())).orElse(false);
    }

    /**
     * Da de alta un usuario nuevo — o, si el correo pertenece a uno ya existente pero
     * DESACTIVADO, lo reactiva en vez de crear un registro duplicado (conserva su historial).
     *
     * <p>La contraseña NUNCA la captura quien da de alta: siempre se genera una temporal al
     * azar y se le manda por correo, marcando {@link User#getMustChangePassword()}. Si el
     * rol es SUPERVISOR (solo SUPER_ADMIN puede asignarlo), el usuario queda sin tienda
     * propia y en la misma operación se le asignan las tiendas que va a administrar y el
     * máximo de tiendas de su plan.</p>
     *
     * @param req datos del usuario nuevo
     * @param actor usuario que lo da de alta; queda registrado como {@code createdBy}
     * @return el usuario creado o reactivado
     * @throws FieldConflictException (campo {@code email}) si el correo ya está registrado y ACTIVO
     */
    @Transactional
    public User create(UserRequest req, User actor) {
        Optional<User> existing = userRepository.findByEmail(req.getEmail());
        if (existing.isPresent()) {
            User found = existing.get();
            if (Boolean.TRUE.equals(found.getIsActive())) {
                throw new FieldConflictException("email", "El correo ya está registrado");
            }
            return reactivate(found, req, actor);
        }
        Role role = roleService.findById(req.getRoleId(), actor);
        assertCanAssignRole(role, actor);
        Tienda tienda = resolveTiendaForWrite(req, actor, role);
        if (tienda == null && !isPlatformRole(role)) {
            throw new IllegalStateException("Elige una tienda para poder dar de alta un usuario");
        }
        role = reconcileRoleForTienda(role, tienda);
        String tempPassword = generateTempPassword();
        User u = new User();
        u.setName(req.getName());
        u.setEmail(req.getEmail());
        u.setPassword(passwordEncoder.encode(tempPassword));
        u.setMustChangePassword(true);
        u.setRole(role);
        u.setTienda(tienda);
        u.setIsActive(true);
        u.setCreatedBy(actor);
        User saved = userRepository.save(u);
        applyMaxTiendas(saved, req.getMaxTiendas(), actor);
        applySupervisedTiendas(saved, req.getSupervisedTiendaIds(), actor);
        emailService.sendNewUserPasswordEmail(saved, tempPassword, frontendUrl + "/login");
        return saved;
    }

    /**
     * Reactiva un usuario previamente dado de baja sobre EL MISMO registro (mismo id), con
     * la misma validación de jerarquía/tienda y la misma mecánica de contraseña temporal
     * por correo que un alta nueva (ver {@link #create}).
     */
    private User reactivate(User existing, UserRequest req, User actor) {
        Role role = roleService.findById(req.getRoleId(), actor);
        assertCanAssignRole(role, actor);
        Tienda tienda = resolveTiendaForWrite(req, actor, role);
        if (tienda == null && !isPlatformRole(role)) {
            throw new IllegalStateException("Elige una tienda para poder reactivar este usuario");
        }
        role = reconcileRoleForTienda(role, tienda);
        String tempPassword = generateTempPassword();
        existing.setName(req.getName());
        existing.setPassword(passwordEncoder.encode(tempPassword));
        existing.setMustChangePassword(true);
        existing.setRole(role);
        existing.setTienda(tienda);
        existing.setIsActive(true);
        existing.setDeletedBy(null);
        existing.setDeletedAt(null);
        existing.setUpdatedBy(actor);
        User saved = userRepository.save(existing);
        applyMaxTiendas(saved, req.getMaxTiendas(), actor);
        applySupervisedTiendas(saved, req.getSupervisedTiendaIds(), actor);
        emailService.sendNewUserPasswordEmail(saved, tempPassword, frontendUrl + "/login");
        return saved;
    }

    /**
     * Genera una contraseña temporal aleatoria y criptográficamente segura, para dar de
     * alta un usuario nuevo (ver {@link #create}).
     */
    private String generateTempPassword() {
        StringBuilder sb = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            sb.append(TEMP_PASSWORD_CHARS.charAt(secureRandom.nextInt(TEMP_PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }

    /**
     * Actualiza los datos de un usuario existente.
     *
     * <p>La contraseña solo se actualiza si viene un valor no vacío (y queda marcado
     * {@code mustChangePassword}). Ascender a alguien a un rol de plataforma le quita su
     * tienda fija; bajarlo de uno le asigna la tienda indicada (o en la que esté actuando
     * quien edita). El cambio de tienda solo lo puede hacer SUPER_ADMIN (a cualquiera) o
     * SUPERVISOR (solo a una de las suyas), y se rechaza si el usuario tiene un corte de
     * caja abierto.</p>
     *
     * @param id id del usuario a actualizar
     * @param req nuevos valores
     * @param actor usuario que hace el cambio; queda registrado como {@code updatedBy}
     * @return el usuario actualizado
     */
    @Transactional
    public User update(Long id, UserRequest req, User actor) {
        User u = findById(id, actor);
        assertCanManage(u, actor);
        userRepository.findByEmail(req.getEmail())
                .filter(other -> !other.getId().equals(u.getId()))
                .ifPresent(other -> { throw new FieldConflictException("email", "El correo ya está registrado"); });
        u.setName(req.getName());
        u.setEmail(req.getEmail());
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            validatePasswordLength(req.getPassword());
            u.setPassword(passwordEncoder.encode(req.getPassword()));
            u.setMustChangePassword(true);
        }
        if (req.getRoleId() != null) {
            Role role = roleService.findById(req.getRoleId(), actor);
            // Editar su PROPIO rol no pasa por la jerarquía (assertCanManage ya lo exceptúa).
            if (!u.getId().equals(actor.getId()) && !role.getId().equals(u.getRole().getId())) {
                assertCanAssignRole(role, actor);
            }
            u.setRole(role);
            // Un Supervisor no tiene tienda propia: la administra vía Tienda.supervisor.
            if (isPlatformRole(role)) u.setTienda(null);
        }
        if (req.getTiendaId() != null && !isPlatformRole(u.getRole()) && tenantScope.isPlatformActor(actor)) {
            if (!tenantScope.canManageTienda(actor, req.getTiendaId())) {
                throw new AccessDeniedException("Esa tienda no está bajo tu administración");
            }
            Tienda target = resolveTienda(req.getTiendaId());
            Long currentTiendaId = u.getTienda() != null ? u.getTienda().getId() : null;
            // Un corte abierto queda ligado a la tienda donde se abrió: mover a quien lo
            // abrió lo dejaría huérfano, sin nadie en esa tienda que pueda cerrarlo.
            if (!target.getId().equals(currentTiendaId)
                    && cashCutRepository.findFirstByUserIdAndStatus(u.getId(), CashCutStatus.OPEN).isPresent()) {
                throw new IllegalStateException(
                        "No puedes cambiar de tienda a un usuario con un corte de caja abierto. Ciérralo primero.");
            }
            u.setTienda(target);
        } else if (!isPlatformRole(u.getRole()) && u.getTienda() == null) {
            // Se le quitó (o nunca tuvo) una tienda y su rol actual SÍ la necesita — típicamente
            // al "bajar" a alguien de SUPERVISOR a un rol normal sin mandar tiendaId. Sin esto
            // quedaría con rol normal y tienda null, que varias consultas leen como "sin filtro".
            Tienda fallback = tenantScope.tiendaForWrite(actor);
            if (fallback == null) {
                throw new IllegalStateException("Elige una tienda para poder asignarle este rol a este usuario");
            }
            u.setTienda(fallback);
        }
        // Las filas de Role están duplicadas por tienda: si el usuario terminó en otra tienda,
        // se le reasigna el rol del MISMO NOMBRE de su tienda nueva.
        u.setRole(reconcileRoleForTienda(u.getRole(), u.getTienda()));
        u.setUpdatedBy(actor);
        User saved = userRepository.save(u);
        applyMaxTiendas(saved, req.getMaxTiendas(), actor);
        applySupervisedTiendas(saved, req.getSupervisedTiendaIds(), actor);
        return saved;
    }

    /**
     * Determina a qué tienda queda asignado un usuario nuevo: ninguna si el rol es de
     * plataforma; para SUPER_ADMIN, la del request (o la que esté actuando); para
     * SUPERVISOR, la que esté actuando (verificada como suya); para el resto, la propia.
     */
    private Tienda resolveTiendaForWrite(UserRequest req, User actor, Role role) {
        if (isPlatformRole(role)) return null;
        if (tenantScope.isSuperAdmin(actor)) {
            return req.getTiendaId() != null ? resolveTienda(req.getTiendaId()) : tenantScope.tiendaForWrite(actor);
        }
        if (tenantScope.isSupervisor(actor)) {
            return tenantScope.tiendaForWrite(actor);
        }
        return actor.getTienda();
    }

    /**
     * Si el rol dado no pertenece a la tienda dada, lo reemplaza por el rol del MISMO NOMBRE
     * en esa tienda (las filas de {@link Role} están duplicadas por tienda). Sin cambios para
     * roles de plataforma ni cuando {@code tienda} es {@code null}.
     *
     * @throws IllegalStateException si la tienda destino no tiene un rol con ese nombre
     */
    private Role reconcileRoleForTienda(Role role, Tienda tienda) {
        if (role == null || tienda == null || isPlatformRole(role)) return role;
        Long roleTiendaId = role.getTienda() != null ? role.getTienda().getId() : null;
        if (tienda.getId().equals(roleTiendaId)) return role;
        return roleRepository.findByNameAndTiendaId(role.getName(), tienda.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "La tienda elegida no tiene un rol llamado \"" + role.getName()
                                + "\" — créalo ahí primero o elige un rol distinto."));
    }

    // BCrypt (usado por PasswordEncoder) ignora silenciosamente cualquier caracter después
    // del 72; sin ese límite superior, dos contraseñas distintas que solo difieran después
    // de ese punto se validarían como iguales al hacer login.
    private void validatePasswordLength(String password) {
        if (password.length() < 6 || password.length() > 72) {
            throw new FieldConflictException("password", "La contraseña debe tener entre 6 y 72 caracteres");
        }
    }

    private Tienda resolveTienda(Long tiendaId) {
        return tiendaRepository.findById(tiendaId)
                .orElseThrow(() -> new IllegalArgumentException("Tienda no encontrada: " + tiendaId));
    }

    /**
     * Fija el máximo de tiendas del plan contratado de un SUPERVISOR (null = sin límite).
     * Solo lo puede hacer SUPER_ADMIN; si el usuario ya no es SUPERVISOR, se limpia.
     */
    private void applyMaxTiendas(User target, Integer maxTiendas, User actor) {
        if (!tenantScope.isSuperAdmin(actor)) return;
        Integer next = isSupervisorRole(target.getRole()) ? maxTiendas : null;
        if (!Objects.equals(target.getMaxTiendas(), next)) {
            target.setMaxTiendas(next);
            userRepository.save(target);
        }
    }

    /**
     * Aplica (reemplazando por completo) el conjunto de tiendas que un usuario SUPERVISOR
     * administra. Si el usuario YA NO es SUPERVISOR, le quita todas las que tuviera, para
     * que no queden "supervisadas por" alguien que ya no lo es.
     *
     * @param target usuario ya guardado
     * @param supervisedTiendaIds tiendas deseadas; {@code null} = no tocar la asignación
     *                            actual; lista vacía = quitarle todas
     * @param actor quien hace el cambio; se ignora silenciosamente si no es SUPER_ADMIN
     */
    private void applySupervisedTiendas(User target, List<Long> supervisedTiendaIds, User actor) {
        if (!tenantScope.isSuperAdmin(actor)) return;
        if (!isSupervisorRole(target.getRole())) {
            for (Tienda t : tiendaRepository.findBySupervisorIdOrderByNameAsc(target.getId())) {
                t.setSupervisor(null);
                tiendaRepository.save(t);
            }
            return;
        }
        if (supervisedTiendaIds == null) return;

        List<Tienda> current = tiendaRepository.findBySupervisorIdOrderByNameAsc(target.getId());
        List<Tienda> desired = supervisedTiendaIds.isEmpty() ? List.of() : tiendaRepository.findAllById(supervisedTiendaIds);

        for (Tienda t : current) {
            if (desired.stream().noneMatch(d -> d.getId().equals(t.getId()))) {
                t.setSupervisor(null);
                tiendaRepository.save(t);
            }
        }
        for (Tienda t : desired) {
            Long currentSupervisorId = t.getSupervisor() != null ? t.getSupervisor().getId() : null;
            if (!target.getId().equals(currentSupervisorId)) {
                t.setSupervisor(target);
                tiendaRepository.save(t);
            }
        }
    }

    /**
     * Da de baja (borrado suave) un usuario: marca {@code isActive=false} y registra
     * quién y cuándo lo eliminó, sin borrar la fila.
     *
     * @param id id del usuario a desactivar
     * @param actor usuario que lo desactiva; debe tener acceso a la tienda del usuario objetivo
     */
    public void deactivate(Long id, User actor) {
        User u = findById(id, actor);
        assertCanManage(u, actor);
        u.setIsActive(false);
        u.setDeletedBy(actor);
        u.setDeletedAt(LocalDateTime.now());
        userRepository.save(u);
    }
}
