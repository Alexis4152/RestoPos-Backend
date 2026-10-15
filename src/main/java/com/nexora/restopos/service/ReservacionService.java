package com.nexora.restopos.service;

import com.nexora.restopos.dto.ReservacionDtos;
import com.nexora.restopos.model.*;
import com.nexora.restopos.repository.MesaRepository;
import com.nexora.restopos.repository.ReservacionRepository;
import com.nexora.restopos.repository.ZonaRepository;
import com.nexora.restopos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * Reservaciones de mesa: el cliente la pide desde la página web (queda PENDIENTE) o el personal
 * la captura cuando llaman por teléfono (queda CONFIRMADA). El personal la confirma asignando
 * mesa, la rechaza con motivo, y el día de la visita marca si llegaron o no.
 * <p>
 * Las necesidades especiales (periqueras, mesa accesible, carriola, ocasión, notas) solo se
 * guardan y se muestran destacadas al personal: no cambian las reglas de asignación.
 * <p>
 * Una mesa no se bloquea con la reservación (seguiría "libre" días antes): solo se avisa en el
 * tablero de mesas y se impide asignar la misma mesa a dos reservaciones con menos de
 * {@link #DURACION_HORAS} horas de diferencia.
 */
@Service
@RequiredArgsConstructor
public class ReservacionService {

    /** Tiempo que se considera ocupada una mesa reservada (para detectar choques). */
    static final int DURACION_HORAS = 2;
    /** Tope de reservaciones activas a futuro con el mismo teléfono (anti-spam). */
    private static final int MAX_ACTIVAS_POR_TELEFONO = 3;
    private static final EnumSet<ReservacionEstado> ACTIVAS = EnumSet.of(ReservacionEstado.PENDIENTE, ReservacionEstado.CONFIRMADA);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter FECHA_TEXTO =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'a las' HH:mm", new Locale("es", "MX"));

    private final ReservacionRepository reservacionRepository;
    private final ZonaRepository zonaRepository;
    private final MesaRepository mesaRepository;
    private final TenantScope tenantScope;
    private final EmailService emailService;
    private final com.nexora.restopos.repository.TiendaInfoRepository tiendaInfoRepository;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    // ── Página web ──────────────────────────────────────────────────────────────────

    public ReservacionDtos.Opciones opciones(TiendaInfo info) {
        exigirActivas(info);
        List<ReservacionDtos.ZonaOpcion> zonas = zonaRepository.findAllForTienda(info.getTienda().getId()).stream()
                .map(z -> new ReservacionDtos.ZonaOpcion(z.getId(), z.getNombre()))
                .toList();
        return new ReservacionDtos.Opciones(info.getReservaMaxPersonas(), info.getReservaAnticipacionHoras(),
                info.getReservaDiasMax(), info.getReservaHoraInicio(), info.getReservaHoraFin(), zonas);
    }

    @Transactional
    public Reservacion crearWeb(TiendaInfo info, ReservacionDtos.Crear req, Cliente cliente) {
        exigirActivas(info);
        Tienda tienda = info.getTienda();
        LocalDateTime cuando = req.getFechaHora().withSecond(0).withNano(0);
        LocalDateTime ahora = LocalDateTime.now();

        if (req.getPersonas() > info.getReservaMaxPersonas()) {
            throw new IllegalArgumentException("En línea se puede reservar hasta para " + info.getReservaMaxPersonas()
                    + " personas; para grupos más grandes llámanos");
        }
        if (cuando.isBefore(ahora.plusHours(info.getReservaAnticipacionHoras()))) {
            throw new IllegalArgumentException(info.getReservaAnticipacionHoras() > 0
                    ? "Reserva con al menos " + info.getReservaAnticipacionHoras() + " h de anticipación"
                    : "Elige una fecha y hora futuras");
        }
        if (cuando.toLocalDate().isAfter(LocalDate.now().plusDays(info.getReservaDiasMax()))) {
            throw new IllegalArgumentException("Solo se puede reservar con hasta " + info.getReservaDiasMax() + " días de anticipación");
        }
        LocalTime hora = cuando.toLocalTime();
        if ((info.getReservaHoraInicio() != null && hora.isBefore(info.getReservaHoraInicio()))
                || (info.getReservaHoraFin() != null && hora.isAfter(info.getReservaHoraFin()))) {
            throw new IllegalArgumentException("Esa hora está fuera del horario de reservaciones");
        }
        String telefono = req.getTelefono().trim();
        if (reservacionRepository.contarActivasPorTelefono(tienda.getId(), telefono, ACTIVAS, ahora) >= MAX_ACTIVAS_POR_TELEFONO) {
            throw new IllegalStateException("Ya tienes " + MAX_ACTIVAS_POR_TELEFONO
                    + " reservaciones activas; cancela alguna o llámanos para hacer otra");
        }

        Reservacion r = nueva(tienda, req, cuando);
        r.setCliente(cliente);
        r.setOrigen("WEB");
        r = reservacionRepository.save(r);
        avisar(r);
        return r;
    }

    public ReservacionDtos.Publica porToken(TiendaInfo info, String token) {
        return toPublica(buscarPorToken(info, token));
    }

    /** El cliente cancela (con su código o desde su cuenta) mientras no haya pasado la hora. */
    @Transactional
    public ReservacionDtos.Publica cancelarPorCliente(TiendaInfo info, String token, Cliente cliente) {
        Reservacion r = buscarPorToken(info, token);
        if (cliente != null && (r.getCliente() == null || !r.getCliente().getId().equals(cliente.getId()))) {
            throw new IllegalArgumentException("Reservación no encontrada");
        }
        if (!puedeCancelar(r)) throw new IllegalStateException("Esta reservación ya no se puede cancelar");
        r.setEstado(ReservacionEstado.CANCELADA);
        r.setMotivo("Cancelada por el cliente");
        avisar(r);
        return toPublica(r);
    }

    public List<ReservacionDtos.Publica> delCliente(Cliente cliente) {
        return reservacionRepository.findTop30ByClienteIdOrderByFechaHoraDesc(cliente.getId()).stream()
                .map(this::toPublica).toList();
    }

    // ── Personal ────────────────────────────────────────────────────────────────────

    /** Reservaciones de un día (por hora). */
    public List<ReservacionDtos.Staff> delDia(User actor, LocalDate fecha) {
        LocalDate dia = fecha != null ? fecha : LocalDate.now();
        return reservacionRepository.buscar(tenantScope.scopeId(actor), dia.atStartOfDay(), dia.plusDays(1).atStartOfDay())
                .stream().map(this::toStaff).toList();
    }

    /** Por confirmar, de hoy en adelante (aviso global y bandeja de la pantalla). */
    public List<ReservacionDtos.Staff> pendientes(User actor) {
        return reservacionRepository.pendientes(tenantScope.scopeId(actor), LocalDate.now().atStartOfDay())
                .stream().map(this::toStaff).toList();
    }

    /** Captura del personal (cliente que llamó): sin reglas de horario de la página, confirmada de una vez. */
    @Transactional
    public ReservacionDtos.Staff crearPorPersonal(ReservacionDtos.Crear req, User actor) {
        Tienda tienda = tenantScope.tiendaForWrite(actor);
        if (tienda == null) throw new IllegalStateException("Elige una tienda para capturar la reservación");
        LocalDateTime cuando = req.getFechaHora().withSecond(0).withNano(0);
        if (cuando.isBefore(LocalDateTime.now().minusMinutes(30))) throw new IllegalArgumentException("Elige una fecha y hora futuras");
        Reservacion r = nueva(tienda, req, cuando);
        r.setOrigen("PERSONAL");
        r.setEstado(ReservacionEstado.CONFIRMADA);
        r.setAtendidaPor(actor);
        if (req.getMesaId() != null) r.setMesa(mesaValida(r, req.getMesaId()));
        r = reservacionRepository.save(r);
        avisar(r);
        return toStaff(r);
    }

    @Transactional
    public ReservacionDtos.Staff confirmar(Long id, Long mesaId, User actor) {
        Reservacion r = buscar(id, actor);
        if (r.getEstado() != ReservacionEstado.PENDIENTE && r.getEstado() != ReservacionEstado.CONFIRMADA) {
            throw new IllegalStateException("Solo se puede confirmar una reservación pendiente");
        }
        boolean eraPendiente = r.getEstado() == ReservacionEstado.PENDIENTE;
        r.setMesa(mesaId != null ? mesaValida(r, mesaId) : null);
        r.setEstado(ReservacionEstado.CONFIRMADA);
        r.setAtendidaPor(actor);
        if (eraPendiente) correo(r, "Tu reservación está confirmada",
                "¡Listo! Te esperamos el " + FECHA_TEXTO.format(r.getFechaHora()) + " para " + personasTexto(r)
                        + (r.getMesa() != null ? " (mesa " + r.getMesa().getNumero() + ")" : "") + ".");
        avisar(r);
        return toStaff(r);
    }

    @Transactional
    public ReservacionDtos.Staff rechazar(Long id, String motivo, User actor) {
        Reservacion r = buscar(id, actor);
        if (r.getEstado() != ReservacionEstado.PENDIENTE) throw new IllegalStateException("Solo se puede rechazar una reservación pendiente");
        r.setEstado(ReservacionEstado.RECHAZADA);
        r.setMotivo(textoOr(motivo, "No tenemos lugar disponible en ese horario"));
        r.setAtendidaPor(actor);
        correo(r, "No pudimos confirmar tu reservación",
                "Lo sentimos, no pudimos confirmar tu reservación del " + FECHA_TEXTO.format(r.getFechaHora())
                        + ".\nMotivo: " + r.getMotivo());
        avisar(r);
        return toStaff(r);
    }

    @Transactional
    public ReservacionDtos.Staff cancelar(Long id, String motivo, User actor) {
        Reservacion r = buscar(id, actor);
        if (r.getEstado() != ReservacionEstado.CONFIRMADA && r.getEstado() != ReservacionEstado.PENDIENTE) {
            throw new IllegalStateException("Esta reservación ya no está activa");
        }
        r.setEstado(ReservacionEstado.CANCELADA);
        r.setMotivo(textoOr(motivo, "Cancelada por el restaurante"));
        r.setAtendidaPor(actor);
        correo(r, "Tu reservación fue cancelada",
                "Tu reservación del " + FECHA_TEXTO.format(r.getFechaHora()) + " fue cancelada.\nMotivo: " + r.getMotivo());
        avisar(r);
        return toStaff(r);
    }

    /** Llegada (LLEGO) o ausencia (NO_LLEGO) de una reservación confirmada. */
    @Transactional
    public ReservacionDtos.Staff marcarLlegada(Long id, boolean llego, User actor) {
        Reservacion r = buscar(id, actor);
        boolean corrigiendo = r.getEstado() == ReservacionEstado.LLEGO || r.getEstado() == ReservacionEstado.NO_LLEGO;
        if (r.getEstado() != ReservacionEstado.CONFIRMADA && !corrigiendo) {
            throw new IllegalStateException("Primero confirma la reservación");
        }
        r.setEstado(llego ? ReservacionEstado.LLEGO : ReservacionEstado.NO_LLEGO);
        avisar(r);
        return toStaff(r);
    }

    // ── Auxiliares ──────────────────────────────────────────────────────────────────

    private Reservacion nueva(Tienda tienda, ReservacionDtos.Crear req, LocalDateTime cuando) {
        Zona zona = null;
        if (req.getZonaId() != null) {
            zona = zonaRepository.findById(req.getZonaId())
                    .filter(z -> z.getTienda() != null && z.getTienda().getId().equals(tienda.getId()))
                    .orElseThrow(() -> new IllegalArgumentException("La zona elegida no existe"));
        }
        int periqueras = req.getPeriqueras() == null ? 0 : req.getPeriqueras();
        if (periqueras > req.getPersonas()) throw new IllegalArgumentException("Hay más periqueras que personas");
        return Reservacion.builder()
                .tienda(tienda)
                .nombre(req.getNombre().trim())
                .telefono(req.getTelefono().trim())
                .email(textoOr(req.getEmail(), null))
                .fechaHora(cuando)
                .personas(req.getPersonas())
                .zonaPreferida(zona)
                .periqueras(periqueras)
                .accesible(Boolean.TRUE.equals(req.getAccesible()))
                .carriola(Boolean.TRUE.equals(req.getCarriola()))
                .ocasion(textoOr(req.getOcasion(), null))
                .notas(textoOr(req.getNotas(), null))
                .token(nuevoToken())
                .build();
    }

    /** Mesa de la misma tienda, activa y sin otra reservación confirmada en esa ventana. */
    private Mesa mesaValida(Reservacion r, Long mesaId) {
        Mesa mesa = mesaRepository.findById(mesaId)
                .filter(m -> Boolean.TRUE.equals(m.getIsActive()))
                .filter(m -> m.getTienda() != null && m.getTienda().getId().equals(r.getTienda().getId()))
                .orElseThrow(() -> new IllegalArgumentException("Mesa no encontrada"));
        List<Reservacion> choques = reservacionRepository.choquesDeMesa(mesa.getId(), r.getId() == null ? -1L : r.getId(),
                r.getFechaHora().minusHours(DURACION_HORAS), r.getFechaHora().plusHours(DURACION_HORAS));
        if (!choques.isEmpty()) {
            Reservacion c = choques.get(0);
            throw new IllegalStateException("La mesa " + mesa.getNumero() + " ya está reservada a las "
                    + c.getFechaHora().toLocalTime() + " (" + c.getNombre() + ")");
        }
        return mesa;
    }

    private Reservacion buscar(Long id, User actor) {
        Reservacion r = reservacionRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Reservación no encontrada"));
        Long scope = tenantScope.scopeId(actor);
        if (scope != null && !scope.equals(r.getTienda().getId())) throw new IllegalArgumentException("Reservación no encontrada");
        return r;
    }

    private Reservacion buscarPorToken(TiendaInfo info, String token) {
        return reservacionRepository.findByToken(token == null ? "" : token)
                .filter(r -> r.getTienda().getId().equals(info.getTienda().getId()))
                .orElseThrow(() -> new IllegalArgumentException("Reservación no encontrada"));
    }

    private void exigirActivas(TiendaInfo info) {
        if (!Boolean.TRUE.equals(info.getWebReservasEnabled())) {
            throw new IllegalStateException("Este restaurante no recibe reservaciones en línea");
        }
    }

    private static boolean puedeCancelar(Reservacion r) {
        return (r.getEstado() == ReservacionEstado.PENDIENTE || r.getEstado() == ReservacionEstado.CONFIRMADA)
                && r.getFechaHora().isAfter(LocalDateTime.now());
    }

    private void avisar(Reservacion r) {
        LiveEventsService.registrar(new LiveEventsService.Cambio("reservacion", r.getTienda().getId(), null,
                r.getCliente() != null ? r.getCliente().getId() : null));
    }

    private void correo(Reservacion r, String asunto, String cuerpo) {
        if (r.getEmail() == null) return;
        String tienda = r.getTienda().getName();
        // Enlace para ver (o cancelar) la reservación en la página web sin necesidad de cuenta.
        String enlace = tiendaInfoRepository.findByTiendaId(r.getTienda().getId())
                .map(TiendaInfo::getPublicSlug).filter(sl -> sl != null && !sl.isBlank())
                .map(sl -> "\n\nConsulta tu reservación aquí:\n" + frontendUrl + "/sitio/" + sl + "?reservacion=" + r.getToken())
                .orElse("");
        emailService.sendReservacionEmail(r.getEmail(), asunto + " — " + tienda,
                "Hola " + r.getNombre() + ",\n\n" + cuerpo + enlace + "\n\n" + tienda);
    }

    private static String personasTexto(Reservacion r) {
        return r.getPersonas() + (r.getPersonas() == 1 ? " persona" : " personas");
    }

    private static String nuevoToken() {
        byte[] b = new byte[24];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String textoOr(String s, String porDefecto) {
        return s == null || s.isBlank() ? porDefecto : s.trim();
    }

    private ReservacionDtos.Publica toPublica(Reservacion r) {
        // La mesa solo se le dice al cliente cuando ya está confirmada.
        boolean conMesa = r.getMesa() != null && r.getEstado() == ReservacionEstado.CONFIRMADA;
        return new ReservacionDtos.Publica(r.getId(), r.getToken(), r.getNombre(), r.getFechaHora(), r.getPersonas(),
                r.getZonaPreferida() != null ? r.getZonaPreferida().getNombre() : null,
                conMesa ? r.getMesa().getNumero() : null,
                r.getPeriqueras(), Boolean.TRUE.equals(r.getAccesible()), Boolean.TRUE.equals(r.getCarriola()),
                r.getOcasion(), r.getNotas(), r.getEstado().name(), r.getMotivo(), puedeCancelar(r), r.getCreatedAt());
    }

    private ReservacionDtos.Staff toStaff(Reservacion r) {
        Mesa m = r.getMesa();
        return new ReservacionDtos.Staff(r.getId(), r.getNombre(), r.getTelefono(), r.getEmail(), r.getCliente() != null,
                r.getFechaHora(), r.getPersonas(),
                r.getZonaPreferida() != null ? r.getZonaPreferida().getId() : null,
                r.getZonaPreferida() != null ? r.getZonaPreferida().getNombre() : null,
                m != null ? m.getId() : null, m != null ? m.getNumero() : null, m != null ? m.getCapacidad() : null,
                r.getPeriqueras(), Boolean.TRUE.equals(r.getAccesible()), Boolean.TRUE.equals(r.getCarriola()),
                r.getOcasion(), r.getNotas(), r.getEstado().name(), r.getMotivo(), r.getOrigen(),
                r.getAtendidaPor() != null ? r.getAtendidaPor().getName() : null, r.getCreatedAt());
    }
}
