package com.nexora.restopos.controller;

import com.nexora.restopos.dto.ApiResponse;
import com.nexora.restopos.dto.OpinionDtos;
import com.nexora.restopos.model.User;
import com.nexora.restopos.service.OpinionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** Pantalla "Opiniones" del personal (sección Reportes): opiniones de mesa + reseñas web. */
@RestController
@RequestMapping("/api/opiniones")
@RequiredArgsConstructor
@PreAuthorize("@sectionAccess.check('REPORTS')")
public class OpinionController {

    private final OpinionService opinionService;

    /** QR para descargar: abre las opiniones de la tienda en su página web. */
    @GetMapping("/qr-publico")
    public ResponseEntity<ApiResponse<OpinionDtos.EnlaceQr>> qrPublico(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(opinionService.qrOpinionesPublicas(actor)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<OpinionDtos.Resumen>> resumen(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Integer estrellas,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(opinionService.resumen(actor, desde, hasta, estrellas)));
    }
}
