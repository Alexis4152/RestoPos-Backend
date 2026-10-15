package com.nexora.restopos.service;

import com.nexora.restopos.dto.ClienteRequests;
import com.nexora.restopos.dto.PublicPedidoStatus;
import com.nexora.restopos.dto.PublicSiteDtos;
import com.nexora.restopos.exception.ClienteNoAutenticadoException;
import com.nexora.restopos.model.*;
import com.nexora.restopos.repository.ClienteRepository;
import com.nexora.restopos.repository.ClienteSesionRepository;
import com.nexora.restopos.repository.OrdenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/**
 * Cuentas de cliente de la página web pública: registro, inicio/cierre de sesión, perfil,
 * historial de pedidos y reseña (estrellas + comentario) de un pedido ya terminado.
 * Todo acotado a la tienda del slug: una cuenta de una tienda no sirve en otra.
 */
@Service
@RequiredArgsConstructor
public class ClienteService {

    private static final int SESION_DIAS = 30;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ClienteRepository clienteRepository;
    private final ClienteSesionRepository sesionRepository;
    private final OrdenRepository ordenRepository;
    private final OrdenService ordenService;
    private final PasswordEncoder passwordEncoder;
    private final com.nexora.restopos.repository.ClienteTokenRepository tokenRepository;
    private final EmailService emailService;

    @org.springframework.beans.factory.annotation.Value("${app.frontend.url}")
    private String frontendUrl;

    private static final String VERIFICAR = "VERIFICAR";
    private static final String RESTABLECER = "RESTABLECER";

    @Transactional
    public PublicSiteDtos.ClienteSesion registrar(TiendaInfo info, ClienteRequests.Registro req) {
        Long tiendaId = info.getTienda().getId();
        String email = req.getEmail().trim().toLowerCase();
        validarTelefono(req.getTelefono());
        if (clienteRepository.existsByTiendaIdAndEmailIgnoreCase(tiendaId, email)) {
            throw new IllegalArgumentException("Ya existe una cuenta con ese correo. Inicia sesión.");
        }
        Cliente c = new Cliente();
        c.setTienda(info.getTienda());
        c.setNombre(req.getNombre().trim());
        c.setTelefono(req.getTelefono().trim());
        c.setDireccion(req.getDireccion().trim());
        c.setEmail(email);
        c.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        c.setIsActive(true);
        c.setEmailVerificado(false);
        Cliente saved = clienteRepository.save(c);
        enviarVerificacion(info, saved);
        return nuevaSesion(saved);
    }

    /**
     * Inicia sesión de un cliente. Mismo mensaje si el correo no existe o la contraseña no
     * coincide (no revela qué correos están registrados). Devuelve null si el correo no es de
     * ningún cliente, para que la página pruebe entonces con las cuentas del personal.
     */
    @Transactional
    public PublicSiteDtos.ClienteSesion login(TiendaInfo info, ClienteRequests.Login req) {
        sesionRepository.deleteExpired(LocalDateTime.now());
        Cliente c = clienteRepository.findByTiendaIdAndEmailIgnoreCase(info.getTienda().getId(), req.getEmail().trim())
                .orElse(null);
        if (c == null) return null;
        if (!Boolean.TRUE.equals(c.getIsActive()) || !passwordEncoder.matches(req.getPassword(), c.getPasswordHash())) {
            throw new ClienteNoAutenticadoException("Correo o contraseña incorrectos");
        }
        return nuevaSesion(c);
    }

    @Transactional
    public void logout(String token) {
        if (token != null && !token.isBlank()) sesionRepository.deleteByTokenHash(hash(token));
    }

    /** Cliente de la sesión, o null si no mandó código (pedido como invitado). Código inválido = 401. */
    public Cliente clienteOpcional(TiendaInfo info, String token) {
        return token == null || token.isBlank() ? null : autenticar(info, token);
    }

    /** Cliente dueño de la sesión; 401 si falta, venció o es de otra tienda. */
    public Cliente autenticar(TiendaInfo info, String token) {
        if (token == null || token.isBlank()) throw new ClienteNoAutenticadoException("Inicia sesión para continuar");
        ClienteSesion s = sesionRepository.findByTokenHash(hash(token))
                .filter(x -> x.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new ClienteNoAutenticadoException("Tu sesión expiró, vuelve a iniciar sesión"));
        Cliente c = s.getCliente();
        if (!c.getTienda().getId().equals(info.getTienda().getId()) || !Boolean.TRUE.equals(c.getIsActive())) {
            throw new ClienteNoAutenticadoException("Tu sesión expiró, vuelve a iniciar sesión");
        }
        return c;
    }

    public PublicSiteDtos.ClientePerfil perfil(Cliente c) {
        return new PublicSiteDtos.ClientePerfil(c.getId(), c.getNombre(), c.getTelefono(), c.getDireccion(), c.getEmail(),
                Boolean.TRUE.equals(c.getEmailVerificado()));
    }

    // ── Verificación de correo y recuperación de contraseña ─────────────────────────────

    /** Manda (o vuelve a mandar) el enlace para confirmar el correo; invalida los anteriores. */
    @Transactional
    public void enviarVerificacion(TiendaInfo info, Cliente c) {
        if (Boolean.TRUE.equals(c.getEmailVerificado())) throw new IllegalStateException("Tu correo ya está confirmado");
        String token = nuevoToken(c, VERIFICAR, LocalDateTime.now().plusHours(48));
        emailService.sendClienteVerificacionEmail(c.getEmail(), c.getNombre(), info.getTienda().getName(),
                enlace(info, "verificar", token));
    }

    /** Confirma el correo con el código del enlace. */
    @Transactional
    public PublicSiteDtos.ClientePerfil verificarCorreo(TiendaInfo info, String token) {
        ClienteToken t = tokenValido(info, token, VERIFICAR, "El enlace para confirmar tu correo no es válido o ya venció");
        Cliente c = t.getCliente();
        c.setEmailVerificado(true);
        t.setUsedAt(LocalDateTime.now());
        return perfil(clienteRepository.save(c));
    }

    /**
     * "¿Olvidaste tu contraseña?": si el correo es de un cliente de esta tienda, le manda un
     * enlace de 1 h. Responde igual exista o no la cuenta (no revela qué correos están registrados).
     */
    @Transactional
    public void olvideContrasena(TiendaInfo info, String email) {
        clienteRepository.findByTiendaIdAndEmailIgnoreCase(info.getTienda().getId(), email.trim())
                .filter(c -> Boolean.TRUE.equals(c.getIsActive()))
                .ifPresent(c -> {
                    String token = nuevoToken(c, RESTABLECER, LocalDateTime.now().plusHours(1));
                    emailService.sendClienteRestablecerEmail(c.getEmail(), c.getNombre(), info.getTienda().getName(),
                            enlace(info, "restablecer", token));
                });
    }

    /**
     * Nueva contraseña con el código del enlace: cierra todas las sesiones abiertas de la cuenta
     * y, como el cliente demostró que el correo es suyo, lo marca como confirmado.
     */
    @Transactional
    public void restablecerContrasena(TiendaInfo info, String token, String password) {
        ClienteToken t = tokenValido(info, token, RESTABLECER, "El enlace para restablecer tu contraseña no es válido o ya venció");
        Cliente c = t.getCliente();
        c.setPasswordHash(passwordEncoder.encode(password));
        c.setEmailVerificado(true);
        t.setUsedAt(LocalDateTime.now());
        clienteRepository.save(c);
        sesionRepository.deleteByClienteId(c.getId());
    }

    private String nuevoToken(Cliente c, String tipo, LocalDateTime vence) {
        tokenRepository.invalidarPendientes(c.getId(), tipo, LocalDateTime.now());
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        ClienteToken t = new ClienteToken();
        t.setCliente(c);
        t.setTipo(tipo);
        t.setTokenHash(hash(token));
        t.setExpiresAt(vence);
        tokenRepository.save(t);
        return token;
    }

    private ClienteToken tokenValido(TiendaInfo info, String token, String tipo, String error) {
        return tokenRepository.findByTokenHashAndTipo(hash(token.trim()), tipo)
                .filter(t -> t.getUsedAt() == null && t.getExpiresAt().isAfter(LocalDateTime.now()))
                .filter(t -> t.getCliente().getTienda().getId().equals(info.getTienda().getId()))
                .orElseThrow(() -> new IllegalArgumentException(error));
    }

    private String enlace(TiendaInfo info, String accion, String token) {
        String base = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        return base + "/sitio/" + info.getPublicSlug() + "?" + accion + "=" + token;
    }

    @Transactional
    public PublicSiteDtos.ClientePerfil actualizarPerfil(Cliente c, ClienteRequests.Perfil req) {
        validarTelefono(req.getTelefono());
        c.setNombre(req.getNombre().trim());
        c.setTelefono(req.getTelefono().trim());
        c.setDireccion(req.getDireccion().trim());
        return perfil(clienteRepository.save(c));
    }

    private static final int TERMINADOS_RECIENTES = 10;
    private static final java.util.Set<String> TERMINADO = java.util.Set.of("ENTREGADO", "RECHAZADO");

    /**
     * "Mis pedidos": siempre todos los que siguen en curso, más los terminados (entregados o no
     * aceptados) — sin fechas, solo los {@value #TERMINADOS_RECIENTES} más recientes; con rango
     * de fechas (por fecha del pedido, ambos días incluidos), todos los de ese rango.
     */
    public List<PublicPedidoStatus> misPedidos(Cliente c, java.time.LocalDate desde, java.time.LocalDate hasta) {
        List<PublicPedidoStatus> recientes = ordenRepository.findTop50ByClienteIdOrderByOpenedAtDesc(c.getId()).stream()
                .map(ordenService::estadoParaCliente)
                .toList();
        List<PublicPedidoStatus> enCurso = recientes.stream().filter(p -> !TERMINADO.contains(p.estado())).toList();

        List<PublicPedidoStatus> terminados;
        if (desde == null && hasta == null) {
            terminados = recientes.stream().filter(p -> TERMINADO.contains(p.estado())).limit(TERMINADOS_RECIENTES).toList();
        } else {
            LocalDateTime ini = (desde != null ? desde : java.time.LocalDate.of(2000, 1, 1)).atStartOfDay();
            LocalDateTime fin = (hasta != null ? hasta : java.time.LocalDate.now()).plusDays(1).atStartOfDay();
            if (!fin.isAfter(ini)) throw new IllegalArgumentException("La fecha inicial no puede ser mayor que la final");
            terminados = ordenRepository.findByClienteIdAndOpenedAtBetweenOrderByOpenedAtDesc(c.getId(), ini, fin).stream()
                    .map(ordenService::estadoParaCliente)
                    .filter(p -> TERMINADO.contains(p.estado()))
                    .toList();
        }
        List<PublicPedidoStatus> out = new java.util.ArrayList<>(enCurso);
        out.addAll(terminados);
        return out;
    }

    /** Reseña de un pedido propio ya entregado; una sola vez por pedido. */
    @Transactional
    public PublicPedidoStatus resenar(Cliente c, Long ordenId, ClienteRequests.Resena req) {
        Orden orden = ordenRepository.findById(ordenId)
                .filter(o -> o.getCliente() != null && o.getCliente().getId().equals(c.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado"));
        if (!"ENTREGADO".equals(ordenService.estadoParaCliente(orden).estado())) {
            throw new IllegalStateException("Podrás calificar tu pedido cuando te lo entreguen");
        }
        if (orden.getMesa() != null) throw new IllegalStateException("Las visitas al restaurante se califican por cuenta");
        if (orden.getResenaEstrellas() != null) throw new IllegalStateException("Ya calificaste este pedido");
        orden.setResenaEstrellas(req.getEstrellas());
        orden.setResenaComentario(req.getComentario() != null && !req.getComentario().isBlank() ? req.getComentario().trim() : null);
        orden.setResenaAt(LocalDateTime.now());
        return ordenService.estadoParaCliente(ordenRepository.save(orden));
    }

    private static final java.util.Set<String> TIPOS_REPORTE = java.util.Set.of("BIEN", "MAL", "FALTANTE");

    /**
     * Reporte de cómo llegó un pedido propio ya entregado: "llegó bien", "llegó mal" o "me faltó
     * algo" (con los productos afectados). Una sola vez y solo dentro de las 24 h posteriores a
     * la entrega. Independiente de la calificación con estrellas.
     */
    @Transactional
    public PublicPedidoStatus reportar(Cliente c, Long ordenId, ClienteRequests.Reporte req) {
        Orden orden = ordenRepository.findById(ordenId)
                .filter(o -> o.getCliente() != null && o.getCliente().getId().equals(c.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado"));
        PublicPedidoStatus actual = ordenService.estadoParaCliente(orden);
        if (!"ENTREGADO".equals(actual.estado())) {
            throw new IllegalStateException("Podrás reportar cómo llegó tu pedido cuando te lo entreguen");
        }
        if (orden.getReporteTipo() != null) throw new IllegalStateException("Ya reportaste este pedido");
        if (!actual.puedeReportar()) {
            throw new IllegalStateException("El plazo de " + OrdenService.HORAS_PARA_REPORTAR + " horas para reportar ya terminó");
        }
        String tipo = req.getTipo().trim().toUpperCase();
        if (!TIPOS_REPORTE.contains(tipo)) throw new IllegalArgumentException("Tipo de reporte no válido");

        // Solo productos que de verdad vienen en este pedido (y no el cargo de envío).
        java.util.Set<Long> ids = req.getProductoIds() == null ? java.util.Set.of() : new java.util.HashSet<>(req.getProductoIds());
        List<String> nombres = orden.getItems().stream()
                .filter(i -> ids.contains(i.getId()))
                .filter(i -> !Boolean.TRUE.equals(i.getCargoExtra()) && i.getEstado() != OrdenDetalleEstado.CANCELADO)
                .map(i -> i.getCantidad().stripTrailingZeros().toPlainString() + "× " + i.getProductoNombre())
                .toList();
        if (nombres.size() != ids.size()) throw new IllegalArgumentException("Algún producto no pertenece a este pedido");
        if ("FALTANTE".equals(tipo) && nombres.isEmpty()) throw new IllegalArgumentException("Marca qué producto te faltó");
        if ("BIEN".equals(tipo) && !nombres.isEmpty()) throw new IllegalArgumentException("Si llegó bien no marques productos");

        orden.setReporteTipo(tipo);
        orden.setReporteProductos(nombres.isEmpty() ? null : String.join(", ", nombres));
        orden.setReporteComentario(req.getComentario() != null && !req.getComentario().isBlank() ? req.getComentario().trim() : null);
        orden.setReporteAt(LocalDateTime.now());
        return ordenService.estadoParaCliente(ordenRepository.save(orden));
    }

    private PublicSiteDtos.ClienteSesion nuevaSesion(Cliente c) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        ClienteSesion s = new ClienteSesion();
        s.setCliente(c);
        s.setTokenHash(hash(token));
        s.setExpiresAt(LocalDateTime.now().plusDays(SESION_DIAS));
        sesionRepository.save(s);
        return new PublicSiteDtos.ClienteSesion(token, perfil(c));
    }

    private static void validarTelefono(String telefono) {
        int digitos = telefono.replaceAll("\\D", "").length();
        if (digitos < 10 || digitos > 15) throw new IllegalArgumentException("El teléfono debe tener entre 10 y 15 dígitos");
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
