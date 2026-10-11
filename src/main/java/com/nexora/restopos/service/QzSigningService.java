package com.nexora.restopos.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Firma las conexiones que el frontend hace a QZ Tray para imprimir tickets (ver
 * {@code utils/printer.js} del frontend), usando el par certificado/llave privada generado
 * para esta instalación ({@code qz-keys/} en la raíz del proyecto backend, fuera de git — ver
 * {@code .gitignore}). Puerto directo del mismo servicio de DemoPV.
 * <p>
 * Sin esto, QZ Tray trata cada conexión como "anónima": no puede recordar la autorización
 * del cajero y le pide permiso en cada cobro, sin posibilidad real de marcar "recordar esta
 * decisión" (el checkbox aparece deshabilitado). El certificado puede ser autofirmado — no
 * hace falta comprarlo a QZ — eso solo hace que QZ Tray muestre "sitio no confiable" en vez
 * del ícono verde de confianza total, pero SÍ le permite al cajero recordar la autorización
 * de forma permanente.
 */
@Service
@Slf4j
public class QzSigningService {

    @Value("${app.qz.certificate-path}")
    private String certificatePath;

    @Value("${app.qz.private-key-path}")
    private String privateKeyPath;

    @Value("${app.qz.certificate-base64}")
    private String certificateBase64;

    @Value("${app.qz.private-key-base64}")
    private String privateKeyBase64;

    private String certificatePem;
    private PrivateKey privateKey;

    /**
     * Carga el certificado y la llave privada al arrancar. Si viene configurada la variable en
     * base64 (producción, sin disco persistente para montar archivos), se usa esa; si no, se
     * leen de las rutas de archivo (desarrollo local, con qz-keys/ en el proyecto). Si ninguna
     * de las dos está disponible o falla la carga, solo deja un aviso en el log: la impresión
     * de tickets sigue funcionando igual, simplemente QZ Tray seguirá pidiendo autorización en
     * cada cobro hasta que se generen y configuren estos archivos/variables.
     */
    @PostConstruct
    void load() {
        try {
            certificatePem = readPem(certificateBase64, certificatePath);
            String keyPem = readPem(privateKeyBase64, privateKeyPath)
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(keyPem);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
            log.info("Certificado de firma de QZ Tray cargado ({})", certificateBase64.isBlank() ? certificatePath : "variable de entorno base64");
        } catch (Exception e) {
            log.warn("No se pudo cargar el certificado/llave de QZ Tray: {}. La impresion de tickets sigue funcionando, pero QZ Tray pedira autorizacion en cada cobro.",
                    e.getMessage());
        }
    }

    /** Prioriza el contenido en base64 (producción); si no viene, lee el archivo (local). */
    private String readPem(String base64Content, String filePath) throws Exception {
        if (!base64Content.isBlank()) {
            return new String(Base64.getDecoder().decode(base64Content), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n");
        }
        return Files.readString(Path.of(filePath));
    }

    /** Certificado público en PEM, para que el frontend lo mande vía {@code qz.security.setCertificatePromise}. */
    public String getCertificate() {
        return certificatePem;
    }

    /**
     * Firma {@code data} (el texto que QZ Tray le pasa a {@code qz.security.setSignaturePromise})
     * con SHA512withRSA, algoritmo configurado del lado del frontend vía
     * {@code qz.security.setSignatureAlgorithm('SHA512')} para que ambos lados coincidan.
     */
    public String sign(String data) {
        try {
            Signature signature = Signature.getInstance("SHA512withRSA");
            signature.initSign(privateKey);
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar la solicitud de QZ Tray", e);
        }
    }
}
