package com.nexora.restopos.service;

import com.nexora.restopos.dto.ReportRecipientsRequest;
import com.nexora.restopos.model.CashReportRecipient;
import com.nexora.restopos.model.Tienda;
import com.nexora.restopos.model.User;
import com.nexora.restopos.repository.CashReportRecipientRepository;
import com.nexora.restopos.repository.TiendaRepository;
import com.nexora.restopos.repository.UserRepository;
import com.nexora.restopos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A quién le llega el reporte de fin de día de una tienda. El supervisor de la tienda es el
 * dueño: lo recibe por default sin configurar nada. Él (o un SUPER_ADMIN) puede sumar
 * usuarios de la tienda (admin, cajeros) y correos libres de personas sin acceso al
 * sistema, o dejar de recibirlo él mismo. Si al final no queda nadie (p. ej. la tienda no
 * tiene supervisor y no se configuró nada), se envía a los ADMIN de la tienda, como antes.
 */
@Service
@RequiredArgsConstructor
public class CashReportRecipientService {

    // Mismo patrón simple que el frontend (Users.jsx/POS.jsx); el @Email del request es la
    // validación formal, esto solo descarta lo que @Email deja pasar (p. ej. "a@b" sin punto).
    private static final Pattern EMAIL_RE = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final CashReportRecipientRepository recipientRepository;
    private final UserRepository userRepository;
    private final TiendaRepository tiendaRepository;
    private final TenantScope tenantScope;

    public record Person(Long id, String name, String email, String role) {}

    /** Configuración actual de la tienda y la lista efectiva de correos a los que llegaría hoy. */
    public record RecipientsConfig(Person supervisor,
                                   boolean reportToSupervisor,
                                   List<Person> users,
                                   List<String> emails,
                                   List<String> effective,
                                   boolean canEdit) {}

    @Transactional(readOnly = true)
    public RecipientsConfig get(User actor) {
        if (!tenantScope.isAdminLevel(actor)) throw new AccessDeniedException("Solo un administrador o supervisor puede ver esto");
        Tienda tienda = requireTienda(actor);
        User sup = tienda.getSupervisor();
        List<Person> users = new ArrayList<>();
        List<String> emails = new ArrayList<>();
        for (CashReportRecipient r : recipientRepository.findByTiendaIdOrderByIdAsc(tienda.getId())) {
            if (r.getUser() != null) users.add(new Person(r.getUser().getId(), r.getUser().getName(), r.getUser().getEmail(), roleName(r.getUser())));
            else if (r.getEmail() != null) emails.add(r.getEmail());
        }
        return new RecipientsConfig(
                sup != null ? new Person(sup.getId(), sup.getName(), sup.getEmail(), roleName(sup)) : null,
                !Boolean.FALSE.equals(tienda.getReportToSupervisor()),
                users, emails, new ArrayList<>(resolveEmails(tienda)), canEdit(actor));
    }

    /** Guarda la configuración (solo SUPERVISOR o SUPER_ADMIN). */
    @Transactional
    public RecipientsConfig update(ReportRecipientsRequest req, User actor) {
        if (!canEdit(actor)) throw new AccessDeniedException("Solo el supervisor de la tienda puede configurar los destinatarios");
        Tienda tienda = requireTienda(actor);

        Set<String> emails = new LinkedHashSet<>();
        for (String raw : req.getEmails() != null ? req.getEmails() : List.<String>of()) {
            String e = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
            if (e.isEmpty()) continue;
            if (!EMAIL_RE.matcher(e).matches()) throw new IllegalStateException("El correo «" + raw + "» no tiene un formato válido");
            emails.add(e);
        }
        Set<Long> userIds = new LinkedHashSet<>(req.getUserIds() != null ? req.getUserIds() : List.of());
        List<User> users = new ArrayList<>();
        for (Long id : userIds) {
            User u = userRepository.findById(id)
                    .filter(x -> x.getTienda() != null && x.getTienda().getId().equals(tienda.getId()) && Boolean.TRUE.equals(x.getIsActive()))
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado en esta tienda"));
            users.add(u);
        }

        tienda.setReportToSupervisor(req.getReportToSupervisor() == null || req.getReportToSupervisor());
        tiendaRepository.save(tienda);
        recipientRepository.deleteByTiendaId(tienda.getId());
        recipientRepository.flush();
        for (User u : users) recipientRepository.save(CashReportRecipient.builder().tienda(tienda).user(u).build());
        for (String e : emails) recipientRepository.save(CashReportRecipient.builder().tienda(tienda).email(e).build());
        return get(actor);
    }

    /**
     * Correos a los que se manda el reporte de fin de día de la tienda: supervisor (si no lo
     * desactivó), usuarios configurados (activos y con correo) y correos libres; si no queda
     * ninguno, los ADMIN de la tienda. Debe llamarse dentro de una transacción (lee relaciones LAZY).
     */
    @Transactional(readOnly = true)
    public Set<String> resolveEmails(Tienda tienda) {
        Set<String> result = new LinkedHashSet<>();
        User sup = tienda.getSupervisor();
        if (!Boolean.FALSE.equals(tienda.getReportToSupervisor()) && sup != null
                && Boolean.TRUE.equals(sup.getIsActive()) && sup.getEmail() != null) {
            result.add(sup.getEmail());
        }
        for (CashReportRecipient r : recipientRepository.findByTiendaIdOrderByIdAsc(tienda.getId())) {
            if (r.getUser() != null) {
                if (Boolean.TRUE.equals(r.getUser().getIsActive()) && r.getUser().getEmail() != null) result.add(r.getUser().getEmail());
            } else if (r.getEmail() != null) {
                result.add(r.getEmail());
            }
        }
        if (result.isEmpty()) {
            for (User admin : userRepository.findAdminsByTiendaId(tienda.getId())) {
                if (admin.getEmail() != null) result.add(admin.getEmail());
            }
        }
        return result;
    }

    private static String roleName(User u) {
        return u.getRole() != null ? u.getRole().getName() : null;
    }

    private boolean canEdit(User actor) {
        return tenantScope.isSupervisor(actor) || tenantScope.isSuperAdmin(actor);
    }

    private Tienda requireTienda(User actor) {
        Tienda tienda = tenantScope.tiendaForWrite(actor);
        if (tienda == null) throw new IllegalStateException("Elige una tienda para continuar");
        return tiendaRepository.findById(tienda.getId()).orElseThrow();
    }
}
