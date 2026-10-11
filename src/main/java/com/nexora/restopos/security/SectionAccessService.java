package com.nexora.restopos.security;

import com.nexora.restopos.model.AppSection;
import com.nexora.restopos.model.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Servicio de autorización por sección (RBAC configurable) expuesto como bean {@code
 * "sectionAccess"} para usarse en expresiones {@code @PreAuthorize("@sectionAccess.check('X')")}
 * en controllers y services. A diferencia de un RBAC fijo en código, las secciones habilitadas
 * ({@link AppSection}: DASHBOARD, POS, INVENTORY, SALES, CASH_CUTS, REPORTS, USERS, ROLES) se
 * definen por {@link com.nexora.restopos.model.Role} y las administra el admin de cada tienda.
 */
@Component("sectionAccess")
public class SectionAccessService {

    /**
     * Verifica que el usuario autenticado tenga habilitada la sección dada en su rol.
     *
     * @param section nombre de un {@link AppSection} (ej. {@code "SALES"})
     * @return {@code true} si el usuario tiene sesión activa, tiene un rol asignado y ese rol
     *         incluye la sección solicitada
     */
    public boolean check(String section) {
        return checkAny(section);
    }

    /**
     * Igual que {@link #check(String)} pero satisfecho si el rol del usuario tiene habilitada
     * al menos una de las secciones dadas.
     *
     * @param sections una o más secciones (nombres de {@link AppSection}); basta con que una
     *                 coincida
     * @return {@code true} si el usuario tiene rol y ese rol incluye alguna de las secciones
     */
    public boolean checkAny(String... sections) {
        User user = currentUser();
        if (user == null || user.getRole() == null) return false;
        for (String code : sections) {
            if (user.getRole().getSections().contains(AppSection.valueOf(code))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Obtiene al usuario autenticado en el contexto de seguridad actual.
     *
     * @return el {@link User} principal de la autenticación actual, o {@code null} si no hay
     *         autenticación o el principal no es un {@code User} (por ejemplo, request anónimo)
     */
    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User)) return null;
        return (User) auth.getPrincipal();
    }
}
