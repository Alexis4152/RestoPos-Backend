package com.nexora.restopos.service;

import com.nexora.restopos.model.Comanda;
import com.nexora.restopos.model.Orden;
import com.nexora.restopos.model.OrdenDetalle;
import com.nexora.restopos.model.Tienda;
import com.nexora.restopos.model.User;
import com.nexora.restopos.repository.ComandaRepository;
import com.nexora.restopos.repository.OrdenDetalleRepository;
import com.nexora.restopos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cola de comandas por imprimir (ver {@link Comanda}). El mesero, al mandar productos de una
 * orden, crea una comanda con esas líneas; la estación de impresión (computadora con QZ Tray)
 * consulta las pendientes, toma cada una y la imprime.
 */
@Service
@RequiredArgsConstructor
public class ComandaService {

    /** Una estación recién encendida no imprime comandas viejas: solo las de este margen. */
    private static final int VENTANA_MINUTOS = 30;
    /** Tras este número de intentos fallidos ya no se reintenta (impresora apagada, etc.). */
    private static final int MAX_INTENTOS = 5;

    private final ComandaRepository comandaRepository;
    private final OrdenDetalleRepository ordenDetalleRepository;
    private final OrdenService ordenService;
    private final ComandaTicketService comandaTicketService;
    private final TenantScope tenantScope;

    /**
     * Crea la comanda de lo que se acaba de mandar de una orden.
     *
     * @param ordenId    orden a la que pertenecen las líneas
     * @param detalleIds ids de las líneas recién agregadas; las que no sean de esta orden se ignoran
     * @return la comanda creada, o {@code null} si no quedó ninguna línea válida
     */
    @Transactional
    public Comanda crear(Long ordenId, List<Long> detalleIds, User actor) {
        Orden orden = ordenService.findById(ordenId, actor);
        if (detalleIds == null || detalleIds.isEmpty()) return null;
        Set<Long> deEstaOrden = ordenDetalleRepository.findByOrdenId(ordenId).stream()
                .map(OrdenDetalle::getId).collect(Collectors.toSet());
        List<Long> validos = detalleIds.stream().filter(Objects::nonNull).distinct().filter(deEstaOrden::contains).toList();
        if (validos.isEmpty()) return null;
        Tienda tienda = orden.getTienda();
        boolean aCocina = tienda == null || !Boolean.FALSE.equals(tienda.getEnviarACocina());
        return comandaRepository.save(Comanda.builder()
                .tiendaId(tienda != null ? tienda.getId() : null)
                .ordenId(ordenId)
                .destino(aCocina ? "COCINA" : "CAJA")
                .detalleIds(validos.stream().map(String::valueOf).collect(Collectors.joining(",")))
                .enviadaPor(actor.getName())
                .build());
    }

    /** Comandas pendientes de la tienda en la que actúa el usuario (las recientes). */
    public List<Comanda> pendientes(User actor) {
        Long tiendaId = tenantScope.scopeId(actor);
        if (tiendaId == null || tiendaId < 0) return List.of();
        return comandaRepository.findTop20ByTiendaIdAndTomadaAtIsNullAndCreatedAtAfterOrderByIdAsc(
                tiendaId, LocalDateTime.now().minusMinutes(VENTANA_MINUTOS));
    }

    /**
     * Toma una comanda para imprimirla y devuelve su ticket ESC/POS.
     *
     * @return {@code {destino, hex}}, o {@code null} si otra estación ya la tomó
     */
    @Transactional
    public Map<String, String> tomar(Long id, User actor) {
        Comanda c = buscar(id, actor);
        if (comandaRepository.tomar(id, LocalDateTime.now()) == 0) return null;
        Orden orden = ordenService.findById(c.getOrdenId(), actor);
        List<Long> ids = Arrays.stream(c.getDetalleIds().split(",")).map(Long::valueOf).toList();
        Map<Long, OrdenDetalle> porId = ordenDetalleRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(OrdenDetalle::getId, Function.identity()));
        List<OrdenDetalle> lineas = ids.stream().map(porId::get).filter(Objects::nonNull).toList();
        return Map.of("destino", c.getDestino(), "hex", comandaTicketService.build(c, orden, lineas));
    }

    /** La impresión falló en la estación: se devuelve a la cola para reintentar (hasta {@value #MAX_INTENTOS}). */
    @Transactional
    public void liberar(Long id, User actor) {
        Comanda c = buscar(id, actor);
        if (c.getIntentos() != null && c.getIntentos() >= MAX_INTENTOS) return;
        c.setTomadaAt(null);
        comandaRepository.save(c);
    }

    private Comanda buscar(Long id, User actor) {
        Comanda c = comandaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Comanda no encontrada"));
        Long scope = tenantScope.scopeId(actor);
        if (scope != null && !scope.equals(c.getTiendaId())) {
            throw new IllegalArgumentException("Comanda no encontrada");
        }
        return c;
    }
}
