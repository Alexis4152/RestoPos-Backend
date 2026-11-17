package com.nexora.restopos.controller;

import com.nexora.restopos.dto.ApiResponse;
import com.nexora.restopos.dto.MailConfigRequest;
import com.nexora.restopos.dto.MailConfigResponse;
import com.nexora.restopos.model.User;
import com.nexora.restopos.service.MailConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Administra la configuración SMTP global (una sola cuenta con la que sale TODO correo del
 * sistema), expuesta bajo {@code /api/admin/mail-config}. Exclusivo de SUPER_ADMIN — es
 * infraestructura de la plataforma, no algo por tienda.
 */
@RestController
@RequestMapping("/api/admin/mail-config")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminMailConfigController {

    private final MailConfigService mailConfigService;

    @GetMapping
    public ResponseEntity<ApiResponse<MailConfigResponse>> get() {
        return ResponseEntity.ok(ApiResponse.ok(mailConfigService.get()));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<MailConfigResponse>> update(@Valid @RequestBody MailConfigRequest request,
                                                                   @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(mailConfigService.update(request, actor), "Configuración de correo actualizada"));
    }
}
