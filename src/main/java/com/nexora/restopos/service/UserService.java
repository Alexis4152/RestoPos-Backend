package com.nexora.restopos.service;

import com.nexora.restopos.dto.UserRequest;
import com.nexora.restopos.model.Role;
import com.nexora.restopos.model.Tienda;
import com.nexora.restopos.model.User;
import com.nexora.restopos.repository.TiendaRepository;
import com.nexora.restopos.repository.UserRepository;
import com.nexora.restopos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * CRUD de usuarios del sistema, con aislamiento por tienda (multi-tenancy) y control de
 * a qué tienda puede asignarse un usuario según quién lo esté dando de alta.
 *
 * <p>Las contraseñas siempre se guardan cifradas vía {@link PasswordEncoder}, nunca en
 * texto plano. La asignación de tienda a un usuario nuevo depende de quién lo crea (ver
 * {@link #resolveTiendaForWrite(UserRequest, User)}): un SUPER_ADMIN puede asignar
 * cualquier tienda (o ninguna, para crear otro usuario de plataforma); cualquier otro
 * actor solo puede dar de alta usuarios dentro de su propia tienda.</p>
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleService roleService;
    private final TiendaRepository tiendaRepository;
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
        return ROLE_RANK.getOrDefault(role.getName(), UNRANKED_ROLE);
    }

    // Evita que, por ejemplo, un ADMIN dé de alta a otro ADMIN, o que un SUPERVISOR asigne
    // el rol SUPER_ADMIN — solo se puede asignar un rol de rango estrictamente inferior al propio.
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

    // Un rol pertenece siempre a una sola tienda (o a ninguna, si es de plataforma) — un
    // SUPER_ADMIN puede elegir rol y tienda por separado en el request, así que hay que
    // validar explícitamente que no combine el rol de una tienda con la tienda de otra (o
    // un rol de plataforma con una tienda, o viceversa). Para cualquier otro actor esto ya
    // queda garantizado de forma implícita por roleService.findById(id, actor), que rechaza
    // roles fuera de su propia tienda.
    private void assertRoleMatchesTienda(Role role, Tienda tienda) {
        Long roleTiendaId = role.getTienda() != null ? role.getTienda().getId() : null;
        Long tiendaId = tienda != null ? tienda.getId() : null;
        if (!java.util.Objects.equals(roleTiendaId, tiendaId)) {
            throw new IllegalArgumentException("El rol seleccionado no pertenece a la tienda indicada");
        }
    }

    /**
     * Busca usuarios aplicando filtros combinables, acotado a la tienda del actor.
     *
     * @param from fecha de alta mínima (inclusiva); si es null se usa un límite inferior
     *             muy antiguo para evitar pasar null al BETWEEN de la consulta
     * @param to fecha de alta máxima (inclusiva); si es null se usa un límite superior
     *           muy lejano, por la misma razón
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
        return userRepository.search(tenantScope.scopeId(actor), effectiveFrom, effectiveTo, name, email, roleId, isActive);
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
     * Busca un usuario por id validando que pertenezca a la tienda del actor.
     *
     * @param id id del usuario
     * @param actor usuario que realiza la consulta
     * @return el usuario encontrado, perteneciente al alcance del actor
     * @throws IllegalArgumentException si no existe o no pertenece a la tienda del actor
     */
    public User findById(Long id, User actor) {
        User u = findById(id);
        Long scope = tenantScope.scopeId(actor);
        if (scope != null && (u.getTienda() == null || !scope.equals(u.getTienda().getId()))) {
            throw new IllegalArgumentException("Usuario no encontrado: " + id);
        }
        return u;
    }

    /**
     * Da de alta un usuario nuevo.
     *
     * <p>La contraseña NUNCA la captura quien da de alta: siempre se genera una temporal al
     * azar ({@link #generateTempPassword()}) y se le manda por correo (ver {@link
     * EmailService#sendNewUserPasswordEmail}), junto con el aviso de que se le va a pedir
     * cambiarla al iniciar sesión ({@link User#getMustChangePassword()} queda en {@code
     * true}) — el campo {@code password} del request, si viene, se ignora a propósito. La
     * tienda del usuario nuevo se resuelve con {@link #resolveTiendaForWrite(UserRequest,
     * User)}: si el actor es SUPER_ADMIN se respeta la tienda solicitada (o ninguna); en
     * cualquier otro caso el usuario nuevo siempre queda en la misma tienda que el actor,
     * sin importar lo que venga en el request.</p>
     *
     * @param req datos del usuario nuevo (nombre, correo, rol, tienda deseada)
     * @param actor usuario que lo da de alta; queda registrado como {@code createdBy}
     * @return el usuario creado
     * @throws IllegalArgumentException si el correo ya está registrado
     */
    public User create(UserRequest req, User actor) {
        Optional<User> existing = userRepository.findByEmail(req.getEmail());
        if (existing.isPresent()) {
            User found = existing.get();
            if (Boolean.TRUE.equals(found.getIsActive())) {
                throw new IllegalArgumentException("El correo ya está registrado");
            }
            return reactivate(found, req, actor);
        }
        Role role = roleService.findById(req.getRoleId(), actor);
        assertCanAssignRole(role, actor);
        Tienda tienda = resolveTiendaForWrite(req, actor);
        assertRoleMatchesTienda(role, tienda);
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
        emailService.sendNewUserPasswordEmail(saved, tempPassword, frontendUrl + "/login");
        return saved;
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
     * Reactiva un usuario previamente dado de baja en vez de crear un registro duplicado,
     * cuando el correo del alta ya pertenecía a una cuenta desactivada — conserva su historial
     * (ventas, cortes de caja, auditoría) bajo el mismo id en vez de perderlo. Misma validación
     * de jerarquía de rol/tienda y misma mecánica de contraseña temporal por correo que un alta
     * nueva (ver {@link #create}).
     *
     * @param existing usuario desactivado a reactivar
     * @param req nuevos datos (nombre, rol, tienda deseada)
     * @param actor usuario que hace el alta; queda registrado como {@code createdBy}
     * @return el usuario reactivado
     */
    private User reactivate(User existing, UserRequest req, User actor) {
        Role role = roleService.findById(req.getRoleId(), actor);
        assertCanAssignRole(role, actor);
        Tienda tienda = resolveTiendaForWrite(req, actor);
        assertRoleMatchesTienda(role, tienda);
        String tempPassword = generateTempPassword();
        existing.setName(req.getName());
        existing.setPassword(passwordEncoder.encode(tempPassword));
        existing.setMustChangePassword(true);
        existing.setRole(role);
        existing.setTienda(tienda);
        existing.setIsActive(true);
        existing.setDeletedBy(null);
        existing.setDeletedAt(null);
        existing.setCreatedBy(actor);
        User saved = userRepository.save(existing);
        emailService.sendNewUserPasswordEmail(saved, tempPassword, frontendUrl + "/login");
        return saved;
    }

    /**
     * Genera una contraseña temporal aleatoria y criptográficamente segura, para dar de
     * alta un usuario nuevo (ver {@link #create}). Evita caracteres ambiguos (0/O, 1/l/I)
     * para que, si alguien la teclea a mano leyéndola del correo, no dude entre cuál es cuál.
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
     * <p>La contraseña solo se actualiza si viene un valor no vacío en el request
     * (dejar el campo en blanco significa "no cambiar la contraseña") — y cuando el admin
     * SÍ la cambia por esta vía, igual que al dar de alta, el usuario no la eligió, así que
     * también queda marcado {@link User#getMustChangePassword()}{@code =true}. El cambio de
     * tienda de un usuario solo lo puede hacer un SUPER_ADMIN; para cualquier otro actor
     * ese campo se ignora aunque venga en el request.</p>
     *
     * @param id id del usuario a actualizar
     * @param req nuevos valores: nombre, correo, contraseña opcional, rol opcional,
     *            tienda opcional (solo aplica si el actor es SUPER_ADMIN)
     * @param actor usuario que hace el cambio; debe tener acceso a la tienda del usuario
     *              objetivo; queda registrado como {@code updatedBy}
     * @return el usuario actualizado
     */
    public User update(Long id, UserRequest req, User actor) {
        User u = findById(id, actor);
        assertCanManage(u, actor);
        u.setName(req.getName());
        u.setEmail(req.getEmail());
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            validatePasswordLength(req.getPassword());
            u.setPassword(passwordEncoder.encode(req.getPassword()));
            u.setMustChangePassword(true);
        }
        Role newRole = u.getRole();
        if (req.getRoleId() != null) {
            newRole = roleService.findById(req.getRoleId(), actor);
            if (!newRole.getId().equals(u.getRole().getId())) assertCanAssignRole(newRole, actor);
        }
        Tienda newTienda = u.getTienda();
        if (tenantScope.isSuperAdmin(actor) && req.getTiendaId() != null) {
            newTienda = resolveTienda(req.getTiendaId());
        }
        assertRoleMatchesTienda(newRole, newTienda);
        u.setRole(newRole);
        u.setTienda(newTienda);
        u.setUpdatedBy(actor);
        return userRepository.save(u);
    }

    // SUPER_ADMIN puede asignar cualquier tienda (o dejar sin tienda); cualquier otro rol
    // siempre da de alta usuarios dentro de su propia tienda, sin importar lo que venga en el request.
    /**
     * Determina a qué tienda queda asignado un usuario nuevo, según el rol del actor
     * que lo está creando (ver regla de negocio en el comentario anterior).
     *
     * @param req request de alta, con la tienda solicitada (solo se respeta para SUPER_ADMIN)
     * @param actor usuario que da de alta al nuevo usuario
     * @return la tienda resuelta para el usuario nuevo (puede ser null solo si el actor
     *         es SUPER_ADMIN y no especificó tienda)
     */
    private Tienda resolveTiendaForWrite(UserRequest req, User actor) {
        if (tenantScope.isSuperAdmin(actor)) {
            return req.getTiendaId() != null ? resolveTienda(req.getTiendaId()) : null;
        }
        if (tenantScope.isSupervisor(actor)) {
            Tienda tienda = tenantScope.tiendaForWrite(actor);
            if (tienda == null) {
                throw new IllegalStateException("Selecciona una tienda antes de dar de alta usuarios");
            }
            return tienda;
        }
        return actor.getTienda();
    }

    // BCrypt (usado por PasswordEncoder) ignora silenciosamente cualquier caracter después
    // del 72; sin ese límite superior, dos contraseñas distintas que solo difieran después
    // de ese punto se validarían como iguales al hacer login.
    private void validatePasswordLength(String password) {
        if (password.length() < 6 || password.length() > 72) {
            throw new IllegalArgumentException("La contraseña debe tener entre 6 y 72 caracteres");
        }
    }

    private Tienda resolveTienda(Long tiendaId) {
        return tiendaRepository.findById(tiendaId)
                .orElseThrow(() -> new IllegalArgumentException("Tienda no encontrada: " + tiendaId));
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
        u.setDeletedAt(java.time.LocalDateTime.now());
        userRepository.save(u);
    }
}
