package com.nexora.restopos.controller;

import com.nexora.restopos.dto.ApiResponse;
import com.nexora.restopos.service.QzSigningService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Expone el certificado público y la firma que el frontend necesita para autenticar sus
 * conexiones a QZ Tray al imprimir tickets (ver {@code utils/printer.js} del frontend y
 * {@link QzSigningService} para el porqué de todo esto).
 * <p>
 * No requiere ninguna regla de autorización especial más allá de estar logueado (igual que
 * el resto de la API): el certificado es público por naturaleza, y firmar solo produce la
 * firma de un texto que QZ Tray genera y ya conoce del otro lado — no hay nada sensible que
 * proteger aquí distinto del resto de la API.
 */
@RestController
@RequestMapping("/api/qz")
@RequiredArgsConstructor
public class QzController {

    private final QzSigningService qzSigningService;

    /** Certificado público en PEM, para {@code qz.security.setCertificatePromise}. */
    @GetMapping("/certificate")
    public ResponseEntity<ApiResponse<String>> certificate() {
        return ResponseEntity.ok(ApiResponse.ok(qzSigningService.getCertificate()));
    }

    /** Firma el texto que QZ Tray manda a {@code qz.security.setSignaturePromise}. */
    @PostMapping("/sign")
    public ResponseEntity<ApiResponse<String>> sign(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok(qzSigningService.sign(body.get("data"))));
    }
}
