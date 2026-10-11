package com.nexora.restopos.controller;

import com.nexora.restopos.dto.ApiResponse;
import com.nexora.restopos.dto.PageResponse;
import com.nexora.restopos.model.Auditoria;
import com.nexora.restopos.model.User;
import com.nexora.restopos.service.AuditoriaService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Bitácora de auditoría, expuesta bajo {@code /api/audit}. Exclusiva de ADMIN/SUPER_ADMIN. */
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<Auditoria>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User actor) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Auditoria> result = auditoriaService.findAll(pageable, actor);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(result), null));
    }
}
