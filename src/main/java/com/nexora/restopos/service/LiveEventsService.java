package com.nexora.restopos.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Avisos en tiempo real (Server-Sent Events) para reemplazar las consultas cada 10 s: cuando
 * una orden, un producto de la orden o una mesa cambia, se avisa a quien está mirando y cada
 * pantalla recarga sus datos en ese momento. El aviso solo dice QUÉ cambió (id de la orden),
 * nunca manda los datos: cada pantalla los vuelve a pedir por su endpoint normal, con sus
 * permisos de siempre.
 * <p>
 * Tres tipos de suscriptor:
 * <ul>
 *   <li>personal de una tienda ({@link #suscribirTienda}) — Cocina, Para llevar, Entregas...;</li>
 *   <li>seguimiento de UN pedido web por su código ({@link #suscribirOrden});</li>
 *   <li>cliente registrado ({@link #suscribirCliente}) — "Mis pedidos".</li>
 * </ul>
 * Los avisos se mandan DESPUÉS del commit de la transacción (si se mandaran antes, la pantalla
 * recargaría y todavía vería los datos viejos). En memoria: con varias instancias del backend
 * habría que pasar los avisos por Redis/PostgreSQL LISTEN-NOTIFY.
 */
@Slf4j
@Service
public class LiveEventsService {

    /** Las conexiones se renuevan solas cada 30 min (el navegador reconecta). */
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;
    /** Clave de los actores de plataforma sin tienda elegida: reciben los avisos de todas. */
    public static final long TODAS = -1L;

    private static LiveEventsService instancia;

    private final Map<Long, Set<SseEmitter>> porTienda = new ConcurrentHashMap<>();
    private final Map<Long, Set<SseEmitter>> porOrden = new ConcurrentHashMap<>();
    private final Map<Long, Set<SseEmitter>> porCliente = new ConcurrentHashMap<>();

    @PostConstruct
    void init() { instancia = this; }

    /** Acceso estático para los listeners de JPA (que Hibernate instancia fuera de Spring). */
    static LiveEventsService get() { return instancia; }

    public SseEmitter suscribirTienda(Long tiendaId) { return suscribir(porTienda, tiendaId == null ? TODAS : tiendaId); }
    public SseEmitter suscribirOrden(Long ordenId) { return suscribir(porOrden, ordenId); }
    public SseEmitter suscribirCliente(Long clienteId) { return suscribir(porCliente, clienteId); }

    private SseEmitter suscribir(Map<Long, Set<SseEmitter>> mapa, Long clave) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        Set<SseEmitter> set = mapa.computeIfAbsent(clave, k -> new CopyOnWriteArraySet<>());
        set.add(emitter);
        Runnable quitar = () -> set.remove(emitter);
        emitter.onCompletion(quitar);
        // Al vencer los 30 min se cierra limpio (el navegador reconecta solo). Sin el complete(),
        // Spring lanza AsyncRequestTimeoutException e intenta responder un JSON de error sobre un
        // stream text/event-stream, lo que deja un error en el log.
        emitter.onTimeout(() -> { quitar.run(); emitter.complete(); });
        emitter.onError(e -> quitar.run());
        // Primer evento: confirma la conexión (y que el proxy no esté reteniendo la respuesta).
        enviar(emitter, set, SseEmitter.event().name("conectado").data("{}"));
        return emitter;
    }

    // ── Publicación (llamada desde CambiosListener, ya después del commit) ───────────────

    void publicarOrden(Long tiendaId, Long ordenId, Long clienteId) {
        String data = "{\"ordenId\":" + ordenId + "}";
        aTienda(tiendaId, "orden", data);
        if (ordenId != null) enviarA(porOrden.get(ordenId), "orden", data);
        if (clienteId != null) enviarA(porCliente.get(clienteId), "orden", data);
    }

    void publicarMesa(Long tiendaId) {
        aTienda(tiendaId, "mesa", "{}");
    }

    void publicarReservacion(Long tiendaId, Long clienteId) {
        aTienda(tiendaId, "reservacion", "{}");
        if (clienteId != null) enviarA(porCliente.get(clienteId), "reservacion", "{}");
    }

    private void aTienda(Long tiendaId, String nombre, String data) {
        if (tiendaId != null) enviarA(porTienda.get(tiendaId), nombre, data);
        enviarA(porTienda.get(TODAS), nombre, data);
    }

    private void enviarA(Set<SseEmitter> set, String nombre, String data) {
        if (set == null) return;
        for (SseEmitter e : set) enviar(e, set, SseEmitter.event().name(nombre).data(data));
    }

    private void enviar(SseEmitter emitter, Set<SseEmitter> set, SseEmitter.SseEventBuilder evento) {
        try {
            emitter.send(evento);
        } catch (IOException | IllegalStateException ex) {
            // El navegador ya se fue (cerró la pestaña, perdió red): se descarta la conexión.
            set.remove(emitter);
            try { emitter.completeWithError(ex); } catch (Exception ignored) { /* ya cerrado */ }
        }
    }

    /**
     * Latido cada 25 s: mantiene viva la conexión a través de proxies que cortan conexiones
     * "inactivas" y detecta (y limpia) los navegadores que ya se desconectaron.
     */
    @Scheduled(fixedRate = 25_000)
    void latido() {
        for (Map<Long, Set<SseEmitter>> mapa : java.util.List.of(porTienda, porOrden, porCliente)) {
            mapa.forEach((clave, set) -> {
                for (SseEmitter e : set) enviar(e, set, SseEmitter.event().comment("ping"));
            });
            mapa.entrySet().removeIf(en -> en.getValue().isEmpty());
        }
    }

    // ── Agrupación por transacción ────────────────────────────────────────────────────

    /** Cambio pendiente de avisar: una orden (con su tienda y cliente), una mesa o una reservación. */
    record Cambio(String tipo, Long tiendaId, Long ordenId, Long clienteId) {}

    private static final Object CLAVE_TX = new Object();

    /**
     * Registra un cambio para avisarlo cuando la transacción termine bien (una sola vez por
     * orden aunque se hayan tocado varios productos). Sin transacción activa, avisa de inmediato.
     */
    @SuppressWarnings("unchecked")
    static void registrar(Cambio cambio) {
        LiveEventsService svc = instancia;
        if (svc == null) return;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            svc.publicar(Set.of(cambio));
            return;
        }
        Set<Cambio> pendientes = (Set<Cambio>) TransactionSynchronizationManager.getResource(CLAVE_TX);
        if (pendientes == null) {
            Set<Cambio> nuevos = new LinkedHashSet<>();
            TransactionSynchronizationManager.bindResource(CLAVE_TX, nuevos);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(CLAVE_TX);
                    if (status == STATUS_COMMITTED) svc.publicar(nuevos);
                }
            });
            pendientes = nuevos;
        }
        pendientes.add(cambio);
    }

    private void publicar(Set<Cambio> cambios) {
        for (Cambio c : cambios) {
            try {
                if ("mesa".equals(c.tipo())) publicarMesa(c.tiendaId());
                else if ("reservacion".equals(c.tipo())) publicarReservacion(c.tiendaId(), c.clienteId());
                else publicarOrden(c.tiendaId(), c.ordenId(), c.clienteId());
            } catch (Exception e) {
                log.warn("No se pudo enviar aviso en tiempo real: {}", e.getMessage());
            }
        }
    }
}
