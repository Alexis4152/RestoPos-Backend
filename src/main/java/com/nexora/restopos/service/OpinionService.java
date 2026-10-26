package com.nexora.restopos.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.nexora.restopos.dto.OpinionDtos;
import com.nexora.restopos.model.*;
import com.nexora.restopos.repository.CuentaRepository;
import com.nexora.restopos.repository.OpinionRepository;
import com.nexora.restopos.repository.TiendaInfoRepository;
import com.nexora.restopos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Opiniones del consumo en el restaurante (órdenes de MESA), una por {@link Cuenta} pagada:
 * estrellas de la experiencia, de la atención del mesero, comentario y 👍/👎 por platillo.
 * <ul>
 *   <li><b>Por QR</b> (sin cuenta): el ticket trae un enlace con un código aleatorio de la cuenta
 *       ({@link Cuenta#getOpinionToken()}) — solo quien tiene el ticket puede opinar.</li>
 *   <li><b>Por "Mis pedidos"</b>: si el mesero ligó la orden a un cliente registrado
 *       ({@link Orden#getCliente()}), el cliente opina con su sesión.</li>
 * </ul>
 * Plazo: {@value #DIAS_PARA_OPINAR} días desde que se pagó la cuenta. Los pedidos web
 * conservan su propia reseña (Orden.resena*); la pantalla "Opiniones" junta ambas.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpinionService {

    public static final int DIAS_PARA_OPINAR = 7;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OpinionRepository opinionRepository;
    private final CuentaRepository cuentaRepository;
    private final TiendaInfoRepository tiendaInfoRepository;
    private final TenantScope tenantScope;
    private final com.nexora.restopos.repository.OrdenRepository ordenRepository;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    // ── Enlace y QR ──────────────────────────────────────────────────────────────────

    /**
     * Enlace público para opinar de esta cuenta, o null si no aplica: solo cuentas de órdenes de
     * mesa, y solo si la tienda tiene su página web activa (el enlace vive en /sitio/{slug}).
     * Genera el código de la cuenta la primera vez que se pide.
     */
    @Transactional
    public String enlace(Cuenta cuenta) {
        Orden orden = cuenta.getOrden();
        if (orden == null || orden.getMesa() == null || orden.getTienda() == null) return null;
        TiendaInfo info = tiendaInfoRepository.findByTiendaId(orden.getTienda().getId())
                .filter(i -> Boolean.TRUE.equals(i.getWebEnabled()) && i.getPublicSlug() != null)
                .orElse(null);
        if (info == null) return null;
        if (cuenta.getOpinionToken() == null) {
            byte[] bytes = new byte[24];
            RANDOM.nextBytes(bytes);
            cuenta.setOpinionToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
            cuentaRepository.save(cuenta);
        }
        String base = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        return base + "/sitio/" + info.getPublicSlug() + "/opinion/" + cuenta.getOpinionToken();
    }

    /** PNG del código QR de un texto (el enlace de opinión). */
    public static byte[] qrPng(String texto, int tamano) {
        try {
            BitMatrix m = new QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, tamano, tamano,
                    Map.of(EncodeHintType.MARGIN, 1));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(m, "PNG", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el código QR", e);
        }
    }

    /** Enlace + QR de una cuenta para mostrárselo al cliente en pantalla (personal). */
    @Transactional
    public OpinionDtos.EnlaceQr enlaceQr(Cuenta cuenta) {
        String url = enlace(cuenta);
        if (url == null) {
            throw new IllegalStateException("Para generar el QR de opinión la cuenta debe ser de una mesa y la página web de la tienda debe estar activa");
        }
        return new OpinionDtos.EnlaceQr(url, "data:image/png;base64," + Base64.getEncoder().encodeToString(qrPng(url, 360)));
    }

    // ── Cliente: ver y opinar ───────────────────────────────────────────────────────

    /** La cuenta del QR, para la página pública de opinión. */
    @Transactional(readOnly = true)
    public OpinionDtos.CuentaParaOpinar porToken(TiendaInfo info, String token) {
        return vista(cuentaPorToken(info, token));
    }

    @Transactional
    public OpinionDtos.CuentaParaOpinar opinarPorToken(TiendaInfo info, String token, OpinionDtos.Request req) {
        return guardar(cuentaPorToken(info, token), req, "QR", null);
    }

    /** Cuentas (pagadas) de una orden de mesa propia del cliente registrado. */
    @Transactional(readOnly = true)
    public List<OpinionDtos.CuentaParaOpinar> cuentasDelCliente(Cliente cliente, Long ordenId) {
        Orden orden = ordenRepository.findById(ordenId)
                .filter(o -> o.getCliente() != null && o.getCliente().getId().equals(cliente.getId()) && o.getMesa() != null)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado"));
        return cuentaRepository.findByOrdenIdOrderByCreatedAtAsc(orden.getId()).stream()
                .filter(c -> c.getEstado() == CuentaEstado.PAGADA)
                .map(this::vista)
                .toList();
    }

    @Transactional
    public OpinionDtos.CuentaParaOpinar opinarComoCliente(Cliente cliente, Long cuentaId, OpinionDtos.Request req) {
        Cuenta cuenta = cuentaRepository.findById(cuentaId)
                .filter(c -> c.getOrden().getCliente() != null && c.getOrden().getCliente().getId().equals(cliente.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Cuenta no encontrada"));
        return guardar(cuenta, req, "CUENTA", cliente);
    }

    private Cuenta cuentaPorToken(TiendaInfo info, String token) {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("Enlace no válido");
        return cuentaRepository.findByOpinionToken(token.trim())
                .filter(c -> c.getOrden().getTienda() != null && c.getOrden().getTienda().getId().equals(info.getTienda().getId()))
                .orElseThrow(() -> new IllegalArgumentException("Este enlace para opinar no es válido"));
    }

    private OpinionDtos.CuentaParaOpinar guardar(Cuenta cuenta, OpinionDtos.Request req, String canal, Cliente cliente) {
        OpinionDtos.CuentaParaOpinar actual = vista(cuenta);
        switch (actual.estado()) {
            case "YA_OPINADA" -> throw new IllegalStateException("Ya dejaste tu opinión de esta cuenta. ¡Gracias!");
            case "VENCIDA" -> throw new IllegalStateException("El plazo de " + DIAS_PARA_OPINAR + " días para opinar ya terminó");
            case "NO_PAGADA" -> throw new IllegalStateException("Podrás opinar cuando se pague la cuenta");
            default -> { }
        }
        Orden orden = cuenta.getOrden();
        if (req.getMeseroEstrellas() != null && orden.getMesero() == null) {
            throw new IllegalArgumentException("Esta cuenta no tiene mesero asignado");
        }
        // Solo platillos que vienen en ESTA cuenta (no los de otro comensal ni el cargo de envío).
        Map<Long, String> deLaCuenta = cuenta.getItems().stream()
                .map(CuentaDetalle::getOrdenDetalle)
                .filter(od -> od != null && !Boolean.TRUE.equals(od.getCargoExtra()))
                .collect(Collectors.toMap(OrdenDetalle::getId, OrdenDetalle::getProductoNombre, (a, b) -> a));

        Opinion op = new Opinion();
        op.setTienda(orden.getTienda());
        op.setCuenta(cuenta);
        op.setOrden(orden);
        op.setMesero(orden.getMesero());
        op.setCliente(cliente);
        op.setCanal(canal);
        op.setEstrellas(req.getEstrellas());
        op.setMeseroEstrellas(req.getMeseroEstrellas());
        op.setComentario(req.getComentario() != null && !req.getComentario().isBlank() ? req.getComentario().trim() : null);
        Set<Long> vistos = new HashSet<>();
        for (OpinionDtos.Platillo p : Optional.ofNullable(req.getPlatillos()).orElse(List.of())) {
            String nombre = deLaCuenta.get(p.getOrdenDetalleId());
            if (nombre == null) throw new IllegalArgumentException("Algún platillo no pertenece a esta cuenta");
            if (!vistos.add(p.getOrdenDetalleId())) continue;
            OpinionPlatillo pl = new OpinionPlatillo();
            pl.setOpinion(op);
            pl.setOrdenDetalleId(p.getOrdenDetalleId());
            pl.setProductoNombre(nombre);
            pl.setMeGusta(p.getMeGusta());
            op.getPlatillos().add(pl);
        }
        opinionRepository.save(op);
        return vista(cuenta);
    }

    private OpinionDtos.CuentaParaOpinar vista(Cuenta cuenta) {
        Orden orden = cuenta.getOrden();
        Opinion op = opinionRepository.findByCuentaId(cuenta.getId()).orElse(null);
        LocalDateTime pagada = cuenta.getClosedAt();
        LocalDateTime hasta = pagada != null ? pagada.plusDays(DIAS_PARA_OPINAR) : null;
        String estado;
        if (op != null) estado = "YA_OPINADA";
        else if (cuenta.getEstado() != CuentaEstado.PAGADA) estado = "NO_PAGADA";
        else if (hasta != null && LocalDateTime.now().isAfter(hasta)) estado = "VENCIDA";
        else estado = "DISPONIBLE";

        List<OpinionDtos.PlatilloCuenta> platillos = cuenta.getItems().stream()
                .filter(cd -> cd.getOrdenDetalle() != null && !Boolean.TRUE.equals(cd.getOrdenDetalle().getCargoExtra()))
                .map(cd -> new OpinionDtos.PlatilloCuenta(cd.getOrdenDetalle().getId(), cd.getOrdenDetalle().getProductoNombre(), cd.getCantidad()))
                .toList();
        return new OpinionDtos.CuentaParaOpinar(
                cuenta.getId(),
                orden.getTienda() != null ? orden.getTienda().getName() : null,
                orden.getMesa() != null ? orden.getMesa().getNumero() : null,
                orden.getMesero() != null ? orden.getMesero().getName() : null,
                pagada != null ? pagada : cuenta.getCreatedAt(),
                cuenta.getTotal(), platillos, estado, hasta, op != null ? hecha(op) : null);
    }

    private static OpinionDtos.OpinionHecha hecha(Opinion op) {
        return new OpinionDtos.OpinionHecha(op.getEstrellas(), op.getMeseroEstrellas(), op.getComentario(),
                op.getPlatillos().stream().map(p -> new OpinionDtos.PlatilloVoto(p.getProductoNombre(), p.getMeGusta())).toList(),
                op.getCreatedAt(), op.getCanal());
    }

    // ── Personal ──────────────────────────────────────────────────────────────────────

    /** Opiniones de las cuentas de una orden (detalle de la orden): cuentaId → opinión. */
    @Transactional(readOnly = true)
    public Map<Long, OpinionDtos.OpinionHecha> deOrden(Long ordenId) {
        Map<Long, OpinionDtos.OpinionHecha> out = new LinkedHashMap<>();
        for (Opinion op : opinionRepository.findByOrdenId(ordenId)) out.put(op.getCuenta().getId(), hecha(op));
        return out;
    }

    /**
     * Opiniones para la página web: todas las de la tienda (mesa + reseñas de pedidos web), las
     * mejores primero (5 estrellas) y luego las más recientes. Sin datos personales: solo el
     * nombre con la inicial del apellido.
     */
    @Transactional(readOnly = true)
    public OpinionDtos.OpinionesPublicas publicas(TiendaInfo info, int pagina, int porPagina) {
        Long tiendaId = info.getTienda().getId();
        LocalDateTime ini = LocalDate.of(2000, 1, 1).atStartOfDay();
        LocalDateTime fin = LocalDateTime.now().plusDays(1);
        List<OpinionDtos.OpinionPublica> todas = new ArrayList<>();
        for (Opinion op : opinionRepository.buscar(tiendaId, ini, fin)) {
            todas.add(new OpinionDtos.OpinionPublica(op.getEstrellas(), op.getComentario(),
                    nombreCorto(op.getCliente() != null ? op.getCliente().getNombre() : null), op.getCreatedAt(), "En el restaurante",
                    op.getPlatillos().stream().filter(p -> Boolean.TRUE.equals(p.getMeGusta())).map(OpinionPlatillo::getProductoNombre).toList()));
        }
        for (Orden o : ordenRepository.findConResenaEntre(tiendaId, ini, fin)) {
            todas.add(new OpinionDtos.OpinionPublica(o.getResenaEstrellas(), o.getResenaComentario(), nombreCorto(o.getClienteNombre()),
                    o.getResenaAt(), "DOMICILIO".equals(o.getTipoEntrega()) ? "Pedido a domicilio" : "Pedido para recoger", List.of()));
        }
        todas.sort(Comparator.comparingInt(OpinionDtos.OpinionPublica::estrellas).reversed()
                .thenComparing(OpinionDtos.OpinionPublica::fecha, Comparator.nullsLast(Comparator.reverseOrder())));
        List<Long> porEstrellas = new ArrayList<>();
        for (int e = 5; e >= 1; e--) { final int x = e; porEstrellas.add(todas.stream().filter(o -> o.estrellas() == x).count()); }
        double promedio = todas.stream().mapToInt(OpinionDtos.OpinionPublica::estrellas).average().orElse(0);
        int paginas = Math.max(1, (int) Math.ceil(todas.size() / (double) porPagina));
        int p = Math.max(0, Math.min(pagina, paginas - 1));
        List<OpinionDtos.OpinionPublica> pag = todas.subList(Math.min(p * porPagina, todas.size()), Math.min((p + 1) * porPagina, todas.size()));
        return new OpinionDtos.OpinionesPublicas(Math.round(promedio * 10) / 10.0, todas.size(), porEstrellas, p, paginas, new ArrayList<>(pag));
    }

    /** "Juan Pérez López" → "Juan P."; sin nombre → "Cliente". */
    private static String nombreCorto(String nombre) {
        if (nombre == null || nombre.isBlank()) return "Cliente";
        String[] partes = nombre.trim().split("\\s+");
        return partes.length == 1 ? partes[0] : partes[0] + " " + Character.toUpperCase(partes[1].charAt(0)) + ".";
    }

    /** QR (para descargar e imprimir) que abre las opiniones de la tienda en su página web. */
    public OpinionDtos.EnlaceQr qrOpinionesPublicas(User actor) {
        Long tiendaId = tenantScope.scopeId(actor);
        TiendaInfo info = tiendaId == null ? null : tiendaInfoRepository.findByTiendaId(tiendaId).orElse(null);
        if (info == null || info.getPublicSlug() == null || info.getPublicSlug().isBlank() || !Boolean.TRUE.equals(info.getWebEnabled())) {
            throw new IllegalStateException("Activa la página web de la tienda (Configuración → Página web) para generar el QR de opiniones");
        }
        String base = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        String url = base + "/sitio/" + info.getPublicSlug() + "?ver=opiniones";
        return new OpinionDtos.EnlaceQr(url, "data:image/png;base64," + Base64.getEncoder().encodeToString(qrPng(url, 720)));
    }

    /**
     * Pantalla "Opiniones": opiniones de mesa + reseñas de pedidos web del rango, con promedio
     * general, promedio de atención por mesero y los platillos con más 👍 / 👎.
     */
    @Transactional(readOnly = true)
    public OpinionDtos.Resumen resumen(User actor, LocalDate desde, LocalDate hasta, Integer estrellas) {
        Long tiendaId = tenantScope.scopeId(actor);
        LocalDateTime ini = (desde != null ? desde : LocalDate.now().minusDays(30)).atStartOfDay();
        LocalDateTime fin = (hasta != null ? hasta : LocalDate.now()).plusDays(1).atStartOfDay();

        List<OpinionDtos.OpinionStaff> filas = new ArrayList<>();
        List<Opinion> mesa = opinionRepository.buscar(tiendaId, ini, fin);
        for (Opinion op : mesa) {
            filas.add(new OpinionDtos.OpinionStaff("MESA", op.getOrden().getId(), op.getCreatedAt(),
                    op.getOrden().getMesa() != null ? "Mesa " + op.getOrden().getMesa().getNumero() : "Mesa",
                    op.getMesero() != null ? op.getMesero().getName() : null,
                    op.getEstrellas(), op.getMeseroEstrellas(), op.getComentario(),
                    op.getPlatillos().stream().map(p -> new OpinionDtos.PlatilloVoto(p.getProductoNombre(), p.getMeGusta())).toList(),
                    op.getCliente() != null ? op.getCliente().getNombre() : null, null));
        }
        for (Orden o : ordenRepository.findConResenaEntre(tiendaId, ini, fin)) {
            filas.add(new OpinionDtos.OpinionStaff("WEB", o.getId(), o.getResenaAt(),
                    "DOMICILIO".equals(o.getTipoEntrega()) ? "Web · domicilio" : "Web · para recoger",
                    o.getMesero() != null ? o.getMesero().getName() : null,
                    o.getResenaEstrellas(), null, o.getResenaComentario(), List.of(),
                    o.getClienteNombre(), o.getReporteTipo()));
        }
        if (estrellas != null) filas.removeIf(f -> !estrellas.equals(f.estrellas()));
        filas.sort(Comparator.comparing(OpinionDtos.OpinionStaff::fecha, Comparator.nullsLast(Comparator.reverseOrder())));

        double promedio = filas.stream().mapToInt(OpinionDtos.OpinionStaff::estrellas).average().orElse(0);
        List<OpinionDtos.MeseroResumen> meseros = mesa.stream()
                .filter(op -> op.getMesero() != null && op.getMeseroEstrellas() != null)
                .collect(Collectors.groupingBy(op -> op.getMesero().getName()))
                .entrySet().stream()
                .map(e -> new OpinionDtos.MeseroResumen(e.getKey(),
                        e.getValue().stream().mapToInt(Opinion::getMeseroEstrellas).average().orElse(0), e.getValue().size()))
                .sorted(Comparator.comparingDouble(OpinionDtos.MeseroResumen::promedio).reversed())
                .toList();
        Map<String, long[]> votos = new TreeMap<>();
        for (Opinion op : mesa) for (OpinionPlatillo p : op.getPlatillos()) {
            long[] v = votos.computeIfAbsent(p.getProductoNombre(), k -> new long[2]);
            v[Boolean.TRUE.equals(p.getMeGusta()) ? 0 : 1]++;
        }
        List<OpinionDtos.PlatilloResumen> platillos = votos.entrySet().stream()
                .map(e -> new OpinionDtos.PlatilloResumen(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .sorted(Comparator.comparingLong(OpinionDtos.PlatilloResumen::noMeGusta).reversed()
                        .thenComparing(Comparator.comparingLong(OpinionDtos.PlatilloResumen::meGusta).reversed()))
                .toList();
        return new OpinionDtos.Resumen(filas.size(), Math.round(promedio * 10) / 10.0, meseros, platillos, filas);
    }
}
