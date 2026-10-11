package com.nexora.restopos.controller;

import com.nexora.restopos.model.Tienda;
import com.nexora.restopos.model.User;
import com.nexora.restopos.security.TenantScope;
import com.nexora.restopos.service.LiveEventsService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Canal de avisos en tiempo real del personal ({@code GET /api/events}, SSE): avisa cuando
 * cambia una orden o una mesa de su tienda — ver {@link LiveEventsService}. Cualquier usuario
 * con sesión puede escucharlo: solo dice el id de lo que cambió, los datos se piden aparte.
 */
@RestController
@RequiredArgsConstructor
public class LiveEventsController {

    private final LiveEventsService liveEventsService;
    private final TenantScope tenantScope;

    @GetMapping(value = "/api/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter eventos(@AuthenticationPrincipal User actor, HttpServletResponse response) {
        sinBuffer(response);
        Tienda tienda = tenantScope.tiendaForWrite(actor);
        return liveEventsService.suscribirTienda(tienda != null ? tienda.getId() : null);
    }

    /** nginx (y otros proxies) retienen la respuesta para comprimirla; en SSE eso la congela. */
    static void sinBuffer(HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
    }
}
