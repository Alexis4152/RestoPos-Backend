package com.nexora.restopos.service;

import com.nexora.restopos.model.AppSection;
import com.nexora.restopos.model.Cuenta;
import com.nexora.restopos.model.CuentaEstado;
import com.nexora.restopos.model.Tienda;
import com.nexora.restopos.model.User;
import com.nexora.restopos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Regla de quién puede imprimir la <b>cuenta por pagar</b> (las 2 copias cliente/comercio que
 * se le entregan al cliente antes de pagar), según {@link Tienda#getImprimeCuenta()}:
 * <ul>
 *   <li>{@code CAJERO}: solo quien cobra (rol con la sección Cortes de caja). Es el default.</li>
 *   <li>{@code MESERO}: solo quien NO cobra (el mesero).</li>
 *   <li>{@code AMBOS}: cualquiera de los dos.</li>
 * </ul>
 * ADMIN, SUPERVISOR y SUPER_ADMIN siempre pueden, sin importar la configuración. Solo aplica a
 * cuentas PENDIENTES: el ticket de una cuenta ya pagada (reimpresión, Ventas) no se restringe.
 * La configuración la define el SUPER_ADMIN (ver {@code TiendaController#updateImprimeCuenta}).
 */
@Component
@RequiredArgsConstructor
public class ImpresionCuentaPolicy {

    public static final Set<String> MODOS = Set.of("CAJERO", "MESERO", "AMBOS");

    private final TenantScope tenantScope;

    /** {@code true} si el actor puede imprimir la cuenta por pagar en esa tienda. */
    public boolean puedeImprimir(User actor, Tienda tienda) {
        if (tenantScope.isAdminLevel(actor)) return true;
        String modo = tienda != null && tienda.getImprimeCuenta() != null ? tienda.getImprimeCuenta() : "CAJERO";
        if ("AMBOS".equals(modo)) return true;
        boolean esCajero = actor.getRole() != null && actor.getRole().getSections() != null
                && actor.getRole().getSections().contains(AppSection.CASH_CUTS);
        return "MESERO".equals(modo) ? !esCajero : esCajero;
    }

    /**
     * Rechaza imprimir una cuenta PENDIENTE si el actor no tiene permiso según la tienda.
     *
     * @throws AccessDeniedException con un mensaje claro de quién sí puede imprimirla
     */
    public void assertPuedeImprimir(Cuenta cuenta, User actor) {
        if (cuenta.getEstado() != CuentaEstado.PENDIENTE) return;
        Tienda tienda = cuenta.getOrden() != null ? cuenta.getOrden().getTienda() : null;
        if (puedeImprimir(actor, tienda)) return;
        String quien = tienda != null && "MESERO".equals(tienda.getImprimeCuenta()) ? "el mesero" : "el cajero";
        throw new AccessDeniedException("En esta tienda la cuenta la imprime " + quien);
    }
}
