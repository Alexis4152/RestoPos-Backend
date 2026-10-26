package com.nexora.restopos.controller;

import com.nexora.restopos.dto.ApiResponse;
import com.nexora.restopos.dto.OpinionDtos;
import com.nexora.restopos.model.TiendaInfo;
import com.nexora.restopos.model.User;
import com.nexora.restopos.repository.TiendaInfoRepository;
import com.nexora.restopos.security.TenantScope;
import com.nexora.restopos.service.OpinionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;

/**
 * "Menú imprimible" (sección Menú): el diseño del menú se arma en el navegador con los
 * platillos de la tienda; aquí solo se da el QR que lleva a su página web (menú en línea).
 */
@RestController
@RequestMapping("/api/menu")
@RequiredArgsConstructor
@PreAuthorize("@sectionAccess.check('MENU')")
public class MenuImprimibleController {

    private final TiendaInfoRepository tiendaInfoRepository;
    private final TenantScope tenantScope;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    /** QR (PNG en base64, alta resolución para imprimir) que abre el menú de la página web. */
    @GetMapping("/qr")
    public ResponseEntity<ApiResponse<OpinionDtos.EnlaceQr>> qr(@AuthenticationPrincipal User actor) {
        Long tiendaId = tenantScope.scopeId(actor);
        TiendaInfo info = tiendaId == null ? null : tiendaInfoRepository.findByTiendaId(tiendaId).orElse(null);
        if (info == null || info.getPublicSlug() == null || info.getPublicSlug().isBlank() || !Boolean.TRUE.equals(info.getWebEnabled())) {
            throw new IllegalStateException("Activa la página web de la tienda (Configuración → Página web) para poner el QR en el menú");
        }
        String base = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        String url = base + "/sitio/" + info.getPublicSlug();
        String png = "data:image/png;base64," + Base64.getEncoder().encodeToString(OpinionService.qrPng(url, 720));
        return ResponseEntity.ok(ApiResponse.ok(new OpinionDtos.EnlaceQr(url, png)));
    }
}
