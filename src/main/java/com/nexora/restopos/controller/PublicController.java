package com.nexora.restopos.controller;

import com.nexora.restopos.dto.ApiResponse;
import com.nexora.restopos.dto.ClienteRequests;
import com.nexora.restopos.dto.PublicPedidoRequest;
import com.nexora.restopos.dto.PublicPedidoStatus;
import com.nexora.restopos.dto.PublicSiteDtos;
import com.nexora.restopos.model.Cliente;
import com.nexora.restopos.model.Orden;
import com.nexora.restopos.model.TiendaInfo;
import com.nexora.restopos.service.ClienteService;
import com.nexora.restopos.service.OrdenService;
import com.nexora.restopos.service.PublicSiteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API de la página web pública de cada tienda ({@code /sitio/{slug}} en el frontend): sin
 * sesión (ver el permitAll de {@code /api/public/**} en SecurityConfig), con límite de
 * peticiones por IP (PublicRateLimitInterceptor). El aislamiento entre tiendas lo da el slug.
 */
@RestController
@RequestMapping("/api/public/r/{slug}")
@RequiredArgsConstructor
public class PublicController {

    private final PublicSiteService publicSiteService;
    private final OrdenService ordenService;
    private final ClienteService clienteService;
    private final com.nexora.restopos.service.LiveEventsService liveEventsService;

    /** Header con el código de sesión del cliente registrado (distinto del JWT del personal). */
    private static final String CLIENTE_HEADER = "X-Cliente-Token";

    @GetMapping
    public ResponseEntity<ApiResponse<PublicSiteDtos.Tienda>> tienda(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.ok(publicSiteService.tienda(slug)));
    }

    @GetMapping("/menu")
    public ResponseEntity<ApiResponse<List<PublicSiteDtos.Categoria>>> menu(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.ok(publicSiteService.menu(slug)));
    }

    @GetMapping("/mesas")
    public ResponseEntity<ApiResponse<List<PublicSiteDtos.Zona>>> mesas(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.ok(publicSiteService.mesas(slug)));
    }

    /** Crea un pedido para recoger o a domicilio; queda "por aceptar" hasta que el personal lo acepte. */
    @PostMapping("/pedidos")
    public ResponseEntity<ApiResponse<PublicSiteDtos.PedidoCreado>> crearPedido(@PathVariable String slug,
                                                                                @Valid @RequestBody PublicPedidoRequest req,
                                                                                @RequestHeader(value = CLIENTE_HEADER, required = false) String clienteToken,
                                                                                @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        TiendaInfo info = publicSiteService.resolve(slug);
        // Con sesión de cliente, el pedido queda en su historial; sin ella, es de invitado.
        Cliente cliente = clienteService.clienteOpcional(info, clienteToken);
        if (cliente != null && !Boolean.TRUE.equals(cliente.getEmailVerificado())) {
            throw new IllegalStateException("Confirma tu correo para hacer pedidos con tu cuenta (revisa tu bandeja de entrada)");
        }
        String key = idempotencyKey != null && idempotencyKey.matches("[A-Za-z0-9-]{8,64}") ? idempotencyKey : null;
        Orden orden;
        try {
            orden = ordenService.crearPedidoWeb(info, req, cliente, key);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Dos envíos idénticos al mismo tiempo: el segundo choca con el índice único de la
            // llave; se devuelve el pedido que creó el primero.
            orden = ordenService.pedidoPorIdempotencia(info, key).orElseThrow(() -> e);
        }
        return ResponseEntity.ok(ApiResponse.ok(
                new PublicSiteDtos.PedidoCreado(orden.getId(), orden.getTotal(), orden.getTrackingToken()),
                "Pedido recibido"));
    }

    /** En qué va un pedido, por su código de seguimiento (el que se entregó al crearlo). */
    @GetMapping("/seguimiento/{token}")
    public ResponseEntity<ApiResponse<PublicPedidoStatus>> estadoPedido(@PathVariable String slug, @PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.ok(ordenService.estadoPedidoWeb(publicSiteService.resolve(slug), token)));
    }

    /** Avisos en tiempo real del seguimiento de UN pedido (SSE), por su código de seguimiento. */
    @GetMapping(value = "/seguimiento/{token}/eventos", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter eventosPedido(@PathVariable String slug, @PathVariable String token,
                                                                                         jakarta.servlet.http.HttpServletResponse response) {
        Long ordenId = ordenService.estadoPedidoWeb(publicSiteService.resolve(slug), token).id();
        LiveEventsController.sinBuffer(response);
        return liveEventsService.suscribirOrden(ordenId);
    }

    // ── Cuentas de cliente ─────────────────────────────────────────────────────────────

    @PostMapping("/clientes/registro")
    public ResponseEntity<ApiResponse<PublicSiteDtos.ClienteSesion>> registro(@PathVariable String slug,
                                                                              @Valid @RequestBody ClienteRequests.Registro req) {
        return ResponseEntity.ok(ApiResponse.ok(clienteService.registrar(publicSiteService.resolve(slug), req), "Cuenta creada"));
    }

    /**
     * Inicio de sesión de cliente. {@code data} null = ese correo no es de ningún cliente de la
     * tienda: la página prueba entonces con la cuenta del personal (repartidor).
     */
    @PostMapping("/clientes/login")
    public ResponseEntity<ApiResponse<PublicSiteDtos.ClienteSesion>> login(@PathVariable String slug,
                                                                           @Valid @RequestBody ClienteRequests.Login req) {
        return ResponseEntity.ok(ApiResponse.ok(clienteService.login(publicSiteService.resolve(slug), req)));
    }

    @PostMapping("/clientes/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@PathVariable String slug,
                                                    @RequestHeader(value = CLIENTE_HEADER, required = false) String token) {
        clienteService.logout(token);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/clientes/me")
    public ResponseEntity<ApiResponse<PublicSiteDtos.ClientePerfil>> me(@PathVariable String slug,
                                                                        @RequestHeader(value = CLIENTE_HEADER, required = false) String token) {
        return ResponseEntity.ok(ApiResponse.ok(clienteService.perfil(clienteService.autenticar(publicSiteService.resolve(slug), token))));
    }

    @PutMapping("/clientes/me")
    public ResponseEntity<ApiResponse<PublicSiteDtos.ClientePerfil>> actualizarPerfil(@PathVariable String slug,
                                                                                      @RequestHeader(value = CLIENTE_HEADER, required = false) String token,
                                                                                      @Valid @RequestBody ClienteRequests.Perfil req) {
        Cliente c = clienteService.autenticar(publicSiteService.resolve(slug), token);
        return ResponseEntity.ok(ApiResponse.ok(clienteService.actualizarPerfil(c, req), "Datos actualizados"));
    }

    /** Avisos en tiempo real de "Mis pedidos" (SSE). */
    @GetMapping(value = "/clientes/me/eventos", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter eventosCliente(@PathVariable String slug,
                                                                                          @RequestHeader(value = CLIENTE_HEADER, required = false) String token,
                                                                                          jakarta.servlet.http.HttpServletResponse response) {
        Cliente c = clienteService.autenticar(publicSiteService.resolve(slug), token);
        LiveEventsController.sinBuffer(response);
        return liveEventsService.suscribirCliente(c.getId());
    }

    /** Confirma el correo con el código del enlace que se le mandó al cliente. */
    @PostMapping("/clientes/verificar")
    public ResponseEntity<ApiResponse<PublicSiteDtos.ClientePerfil>> verificar(@PathVariable String slug,
                                                                               @Valid @RequestBody ClienteRequests.Verificar req) {
        return ResponseEntity.ok(ApiResponse.ok(clienteService.verificarCorreo(publicSiteService.resolve(slug), req.getToken()),
                "¡Correo confirmado!"));
    }

    /** Vuelve a mandar el correo de confirmación al cliente de la sesión. */
    @PostMapping("/clientes/me/reenviar-verificacion")
    public ResponseEntity<ApiResponse<Void>> reenviarVerificacion(@PathVariable String slug,
                                                                  @RequestHeader(value = CLIENTE_HEADER, required = false) String token) {
        TiendaInfo info = publicSiteService.resolve(slug);
        clienteService.enviarVerificacion(info, clienteService.autenticar(info, token));
        return ResponseEntity.ok(ApiResponse.ok(null, "Te enviamos de nuevo el correo de confirmación"));
    }

    /** "¿Olvidaste tu contraseña?" — misma respuesta exista o no la cuenta. */
    @PostMapping("/clientes/olvide")
    public ResponseEntity<ApiResponse<Void>> olvide(@PathVariable String slug, @Valid @RequestBody ClienteRequests.Olvide req) {
        clienteService.olvideContrasena(publicSiteService.resolve(slug), req.getEmail());
        return ResponseEntity.ok(ApiResponse.ok(null, "Si ese correo tiene cuenta, te enviamos un enlace para restablecer tu contraseña"));
    }

    @PostMapping("/clientes/restablecer")
    public ResponseEntity<ApiResponse<Void>> restablecer(@PathVariable String slug, @Valid @RequestBody ClienteRequests.Restablecer req) {
        clienteService.restablecerContrasena(publicSiteService.resolve(slug), req.getToken(), req.getPassword());
        return ResponseEntity.ok(ApiResponse.ok(null, "Listo, ya puedes entrar con tu nueva contraseña"));
    }

    @GetMapping("/clientes/me/pedidos")
    public ResponseEntity<ApiResponse<List<PublicPedidoStatus>>> misPedidos(@PathVariable String slug,
                                                                            @RequestHeader(value = CLIENTE_HEADER, required = false) String token,
                                                                            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate desde,
                                                                            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate hasta) {
        Cliente c = clienteService.autenticar(publicSiteService.resolve(slug), token);
        return ResponseEntity.ok(ApiResponse.ok(clienteService.misPedidos(c, desde, hasta)));
    }

    /** Reporte de cómo llegó un pedido propio (bien / mal / faltó algo), hasta 24 h después de la entrega. */
    @PostMapping("/clientes/me/pedidos/{id}/reporte")
    public ResponseEntity<ApiResponse<PublicPedidoStatus>> reportar(@PathVariable String slug, @PathVariable Long id,
                                                                    @RequestHeader(value = CLIENTE_HEADER, required = false) String token,
                                                                    @Valid @RequestBody ClienteRequests.Reporte req) {
        Cliente c = clienteService.autenticar(publicSiteService.resolve(slug), token);
        return ResponseEntity.ok(ApiResponse.ok(clienteService.reportar(c, id, req), "¡Gracias! Recibimos tu reporte"));
    }

    /** Estrellas (1–5) y comentario de un pedido propio ya entregado. */
    @PostMapping("/clientes/me/pedidos/{id}/resena")
    public ResponseEntity<ApiResponse<PublicPedidoStatus>> resenar(@PathVariable String slug, @PathVariable Long id,
                                                                   @RequestHeader(value = CLIENTE_HEADER, required = false) String token,
                                                                   @Valid @RequestBody ClienteRequests.Resena req) {
        Cliente c = clienteService.autenticar(publicSiteService.resolve(slug), token);
        return ResponseEntity.ok(ApiResponse.ok(clienteService.resenar(c, id, req), "¡Gracias por tu calificación!"));
    }
}
