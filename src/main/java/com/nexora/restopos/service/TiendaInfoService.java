package com.nexora.restopos.service;

import com.nexora.restopos.dto.TiendaInfoRequest;
import com.nexora.restopos.dto.WebConfigRequest;
import com.nexora.restopos.model.Tienda;
import com.nexora.restopos.model.TiendaInfo;
import com.nexora.restopos.model.User;
import com.nexora.restopos.repository.TiendaInfoRepository;
import com.nexora.restopos.repository.TiendaRepository;
import com.nexora.restopos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administra los datos fiscales y de contacto de una tienda (RFC, dirección, razón
 * social, teléfono, redes sociales, etc.), usados principalmente para imprimirse en el
 * ticket de venta en PDF ({@link TicketPdfService}).
 *
 * <p>Cada tienda tiene a lo más un {@link TiendaInfo}. Solo el ADMIN de esa tienda o un
 * SUPER_ADMIN pueden consultarlos o editarlos, validado con
 * {@link TenantScope#canManageTienda(User, Long)}.</p>
 */
@Service
@RequiredArgsConstructor
public class TiendaInfoService {

    private final TiendaInfoRepository tiendaInfoRepository;
    private final TiendaRepository tiendaRepository;
    private final TenantScope tenantScope;

    // Solo el ADMIN de esa tienda (o SUPER_ADMIN) puede ver/editar sus propios datos fiscales.
    /**
     * Obtiene los datos fiscales/de contacto de una tienda.
     *
     * @param tiendaId id de la tienda
     * @param actor usuario que consulta; debe poder administrar esa tienda
     * @return los datos guardados, o un objeto vacío (no persistido) si la tienda
     *         todavía no ha capturado ninguno
     * @throws org.springframework.security.access.AccessDeniedException si el actor no
     *         tiene permiso sobre esa tienda
     */
    public TiendaInfo get(Long tiendaId, User actor) {
        checkAccess(tiendaId, actor);
        return tiendaInfoRepository.findByTiendaId(tiendaId).orElseGet(() -> blankFor(tiendaId));
    }

    // Objeto vacío (no guardado) para que el formulario tenga algo que mostrar
    // cuando la tienda todavía no ha capturado sus datos.
    private TiendaInfo blankFor(Long tiendaId) {
        Tienda tienda = findTienda(tiendaId);
        TiendaInfo info = new TiendaInfo();
        info.setTienda(tienda);
        return info;
    }

    /**
     * Crea o actualiza los datos fiscales/de contacto de una tienda, y de paso
     * actualiza el nombre de la {@link Tienda} asociada (el formulario de datos fiscales
     * también edita el nombre comercial).
     *
     * @param tiendaId id de la tienda
     * @param req nuevos datos fiscales y de contacto
     * @param actor usuario que hace el cambio; debe poder administrar esa tienda; queda
     *              registrado como {@code createdBy} (si es la primera captura) y
     *              {@code updatedBy}
     * @return los datos fiscales ya guardados
     * @throws org.springframework.security.access.AccessDeniedException si el actor no
     *         tiene permiso sobre esa tienda
     * @throws IllegalArgumentException si la tienda no existe
     */
    @Transactional
    public TiendaInfo update(Long tiendaId, TiendaInfoRequest req, User actor) {
        checkAccess(tiendaId, actor);
        Tienda tienda = findTienda(tiendaId);
        tienda.setName(req.getName());
        tienda.setBlindCashCount(Boolean.TRUE.equals(req.getBlindCashCount()));
        if (req.getModoToma() != null) {
            String modo = req.getModoToma().trim().toUpperCase();
            if (!modo.equals("RAPIDO") && !modo.equals("DETALLADO")) {
                throw new IllegalArgumentException("Modo de toma de orden no válido");
            }
            tienda.setModoToma(modo);
        }
        if (req.getPropinaMaxPorcentaje() != null) {
            tienda.setPropinaMaxPorcentaje(req.getPropinaMaxPorcentaje());
        }
        tienda.setUpdatedBy(actor);
        tiendaRepository.save(tienda);

        boolean isNew = tiendaInfoRepository.findByTiendaId(tiendaId).isEmpty();
        TiendaInfo info = tiendaInfoRepository.findByTiendaId(tiendaId).orElseGet(() -> {
            TiendaInfo i = new TiendaInfo();
            i.setTienda(tienda);
            return i;
        });
        if (isNew) info.setCreatedBy(actor);
        info.setUpdatedBy(actor);
        info.setRfc(req.getRfc());
        info.setCalle(req.getCalle());
        info.setColonia(req.getColonia());
        info.setCodigoPostal(req.getCodigoPostal());
        info.setLocalidad(req.getLocalidad());
        info.setEstado(req.getEstado());
        info.setRazonSocial(req.getRazonSocial());
        info.setTelefono(req.getTelefono());
        info.setPaginaWeb(req.getPaginaWeb());
        info.setRedesSociales(req.getRedesSociales());
        info.setNotasAdicionales(req.getNotasAdicionales());
        info.setCostoEnvioDomicilio(req.getCostoEnvioDomicilio());
        return tiendaInfoRepository.save(info);
    }

    /**
     * Guarda la configuración de la página web pública. Reglas: el link (slug) solo admite
     * minúsculas, números y guiones y no puede usarlo otra tienda; si la página queda activa
     * necesita link, al menos una forma de entrega y una de pago, y si acepta transferencia,
     * los datos bancarios completos.
     */
    @Transactional
    public TiendaInfo updateWebConfig(Long tiendaId, WebConfigRequest req, User actor) {
        checkAccess(tiendaId, actor);
        Tienda tienda = findTienda(tiendaId);
        TiendaInfo info = tiendaInfoRepository.findByTiendaId(tiendaId).orElseGet(() -> {
            TiendaInfo i = new TiendaInfo();
            i.setTienda(tienda);
            i.setCreatedBy(actor);
            return i;
        });

        String slug = req.getPublicSlug() != null ? req.getPublicSlug().trim().toLowerCase() : "";
        boolean enabled = Boolean.TRUE.equals(req.getWebEnabled());
        boolean recoger = Boolean.TRUE.equals(req.getWebRecogerEnabled());
        boolean domicilio = Boolean.TRUE.equals(req.getWebDomicilioEnabled());
        boolean efectivo = Boolean.TRUE.equals(req.getWebPagoEfectivo());
        boolean tarjeta = Boolean.TRUE.equals(req.getWebPagoTarjeta());
        boolean transferencia = Boolean.TRUE.equals(req.getWebPagoTransferencia());

        if (!slug.isEmpty()) {
            if (!slug.matches("[a-z0-9]+(-[a-z0-9]+)*") || slug.length() < 3) {
                throw new IllegalArgumentException("El link solo puede tener minúsculas, números y guiones (mínimo 3 caracteres)");
            }
            if (tiendaInfoRepository.existsByPublicSlugIgnoreCaseAndTiendaIdNot(slug, tiendaId)) {
                throw new IllegalArgumentException("Ese link ya lo usa otra tienda, elige otro");
            }
        }
        if (enabled) {
            if (slug.isEmpty()) throw new IllegalArgumentException("Define el link de tu página para poder activarla");
            boolean conReservas = Boolean.TRUE.equals(req.getWebReservasEnabled());
            if (!recoger && !domicilio && !conReservas) {
                throw new IllegalArgumentException("Activa al menos una forma de entrega (recoger o domicilio) o las reservaciones");
            }
            if ((recoger || domicilio) && !efectivo && !tarjeta && !transferencia) throw new IllegalArgumentException("Activa al menos una forma de pago");
        }
        if (transferencia && (isBlank(req.getTransferenciaBanco()) || isBlank(req.getTransferenciaTitular()) || isBlank(req.getTransferenciaClabe()))) {
            throw new IllegalArgumentException("Para aceptar transferencia captura banco, titular y CLABE / cuenta");
        }

        info.setUpdatedBy(actor);
        info.setWebEnabled(enabled);
        info.setPublicSlug(slug.isEmpty() ? null : slug);
        info.setWebMostrarMesas(Boolean.TRUE.equals(req.getWebMostrarMesas()));
        info.setWebRecogerEnabled(recoger);
        info.setWebDomicilioEnabled(domicilio);
        info.setWebPagoEfectivo(efectivo);
        info.setWebPagoTarjeta(tarjeta);
        info.setWebPagoTransferencia(transferencia);
        info.setTransferenciaBanco(trimOrNull(req.getTransferenciaBanco()));
        info.setTransferenciaTitular(trimOrNull(req.getTransferenciaTitular()));
        info.setTransferenciaClabe(trimOrNull(req.getTransferenciaClabe()));
        info.setWebMensaje(trimOrNull(req.getWebMensaje()));

        boolean reservas = Boolean.TRUE.equals(req.getWebReservasEnabled());
        if (reservas && req.getReservaHoraInicio() != null && req.getReservaHoraFin() != null
                && !req.getReservaHoraFin().isAfter(req.getReservaHoraInicio())) {
            throw new IllegalArgumentException("En reservaciones, la última hora debe ser después de la primera");
        }
        info.setWebReservasEnabled(reservas);
        if (req.getReservaMaxPersonas() != null) info.setReservaMaxPersonas(req.getReservaMaxPersonas());
        if (req.getReservaAnticipacionHoras() != null) info.setReservaAnticipacionHoras(req.getReservaAnticipacionHoras());
        if (req.getReservaDiasMax() != null) info.setReservaDiasMax(req.getReservaDiasMax());
        info.setReservaHoraInicio(req.getReservaHoraInicio());
        info.setReservaHoraFin(req.getReservaHoraFin());
        if (req.getReservaTodasMesas() != null) info.setReservaTodasMesas(req.getReservaTodasMesas());
        if (req.getReservaCambiosHoras() != null) info.setReservaCambiosHoras(req.getReservaCambiosHoras());
        if (req.getReservaRetrasoEnabled() != null) info.setReservaRetrasoEnabled(req.getReservaRetrasoEnabled());
        if (req.getReservaRetrasoHoras() != null) info.setReservaRetrasoHoras(req.getReservaRetrasoHoras());
        return tiendaInfoRepository.save(info);
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    private static String trimOrNull(String s) { return isBlank(s) ? null : s.trim(); }

    private Tienda findTienda(Long tiendaId) {
        return tiendaRepository.findById(tiendaId)
                .orElseThrow(() -> new IllegalArgumentException("Tienda no encontrada: " + tiendaId));
    }

    private void checkAccess(Long tiendaId, User actor) {
        if (!tenantScope.canManageTienda(actor, tiendaId)) {
            throw new AccessDeniedException("No tienes permiso sobre los datos de esta tienda");
        }
    }
}
