package com.nexora.restopos.service;

import com.nexora.restopos.exception.OperacionFallidaException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.UUID;

/**
 * Guarda y borra imágenes subidas por los usuarios (logo de tienda, foto de producto) bajo
 * {@code app.uploads.dir}, servidas públicamente vía {@code /uploads/**}. Antes cada servicio
 * repetía esta misma lógica (ver TiendaLogoService / ProductImageService).
 * <p>
 * El tipo de archivo se decide por su CONTENIDO real (firma de los primeros bytes: PNG, JPEG o
 * WEBP), nunca por el {@code Content-Type} que manda el navegador ni por la extensión — los dos
 * los controla quien sube el archivo y se pueden falsificar. La extensión con la que se guarda
 * sale de esa firma, así que un archivo nunca queda guardado con un tipo distinto al real.
 */
@Service
@Slf4j
public class ImageStorageService {

    public static final long MAX_SIZE_BYTES = 3L * 1024 * 1024; // 3 MB

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] RIFF = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP = {'W', 'E', 'B', 'P'};

    @Value("${app.uploads.dir}")
    private String uploadsDir;

    // Imágenes que NO deben ser públicas (comprobantes de transferencia): carpeta aparte que no
    // está mapeada a /uploads/**, solo se leen por un endpoint con sesión del personal.
    @Value("${app.private-uploads.dir}")
    private String privateUploadsDir;

    /**
     * Valida y guarda la imagen; regresa su ruta pública ({@code /uploads/<carpeta>/<archivo>}).
     *
     * @param file     archivo subido
     * @param carpeta  subcarpeta dentro de uploads (ej. "logos", "products")
     * @param prefijo  inicio del nombre del archivo (ej. "tienda-3"); se le agrega un UUID
     * @param etiqueta cómo se le llama en los mensajes de error ("El logo", "La imagen")
     */
    public String guardar(MultipartFile file, String carpeta, String prefijo, String etiqueta) {
        return "/uploads/" + guardarEn(raiz(), file, carpeta, prefijo, etiqueta);
    }

    /**
     * Igual que {@link #guardar}, pero en la carpeta privada (no se sirve por /uploads/**).
     * Regresa la ruta relativa a esa carpeta ({@code <carpeta>/<archivo>}).
     */
    public String guardarPrivado(MultipartFile file, String carpeta, String prefijo, String etiqueta) {
        return guardarEn(raizPrivada(), file, carpeta, prefijo, etiqueta);
    }

    /**
     * Archivo de la carpeta privada por su ruta relativa; null si no existe o si la ruta
     * intenta salirse de la carpeta (ej. con "../").
     */
    public Path leerPrivado(String rutaRelativa) {
        if (rutaRelativa == null || rutaRelativa.isBlank()) return null;
        Path raiz = raizPrivada();
        Path archivo = raiz.resolve(rutaRelativa).normalize();
        if (!archivo.startsWith(raiz) || !Files.isRegularFile(archivo)) return null;
        return archivo;
    }

    /** Borra un archivo de la carpeta privada, si existe. Best effort: un fallo solo se registra. */
    public void borrarPrivado(String rutaRelativa) {
        Path archivo = leerPrivado(rutaRelativa);
        if (archivo == null) return;
        try {
            Files.deleteIfExists(archivo);
        } catch (IOException e) {
            log.warn("No se pudo borrar el archivo privado anterior ({}): {}", rutaRelativa, e.getMessage());
        }
    }

    /** Valida y guarda la imagen bajo {@code raiz/carpeta}; regresa {@code <carpeta>/<archivo>}. */
    private String guardarEn(Path raiz, MultipartFile file, String carpeta, String prefijo, String etiqueta) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Selecciona un archivo de imagen");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException(etiqueta + " no debe pesar más de 3 MB");
        }
        String ext = extensionPorContenido(file);
        if (ext == null) {
            throw new IllegalArgumentException(etiqueta + " debe ser un archivo PNG, JPG o WEBP válido");
        }
        try {
            Path dir = raiz.resolve(carpeta).normalize();
            Files.createDirectories(dir);
            String filename = prefijo + "-" + UUID.randomUUID() + ext;
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, dir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
            }
            return carpeta + "/" + filename;
        } catch (IOException e) {
            throw new OperacionFallidaException("No se pudo guardar " + etiqueta.toLowerCase(), e);
        }
    }

    /**
     * Borra la imagen de esa ruta pública, si existe. Best effort: un fallo solo se registra.
     * Solo borra dentro de la carpeta de uploads (una ruta manipulada con "../" se ignora).
     */
    public void borrar(String rutaPublica) {
        if (rutaPublica == null || rutaPublica.isBlank()) return;
        try {
            Path raiz = raiz();
            Path archivo = raiz.resolve(rutaPublica.replaceFirst("^/uploads/", "")).normalize();
            if (!archivo.startsWith(raiz)) {
                log.warn("Se ignoró el borrado de una ruta fuera de uploads: {}", rutaPublica);
                return;
            }
            Files.deleteIfExists(archivo);
        } catch (IOException e) {
            log.warn("No se pudo borrar la imagen anterior ({}): {}", rutaPublica, e.getMessage());
        }
    }

    private Path raiz() {
        return Paths.get(uploadsDir).toAbsolutePath().normalize();
    }

    private Path raizPrivada() {
        return Paths.get(privateUploadsDir).toAbsolutePath().normalize();
    }

    /** ".png", ".jpg" o ".webp" según la firma de los primeros bytes; null si no es ninguna. */
    private static String extensionPorContenido(MultipartFile file) {
        byte[] cabecera = new byte[12];
        int leidos;
        try (InputStream in = file.getInputStream()) {
            leidos = in.readNBytes(cabecera, 0, cabecera.length);
        } catch (IOException e) {
            return null;
        }
        if (leidos >= 8 && empiezaCon(cabecera, PNG, 0)) return ".png";
        if (leidos >= 3 && empiezaCon(cabecera, JPEG, 0)) return ".jpg";
        if (leidos >= 12 && empiezaCon(cabecera, RIFF, 0) && empiezaCon(cabecera, WEBP, 8)) return ".webp";
        return null;
    }

    private static boolean empiezaCon(byte[] datos, byte[] firma, int desde) {
        return Arrays.equals(Arrays.copyOfRange(datos, desde, desde + firma.length), firma);
    }
}
