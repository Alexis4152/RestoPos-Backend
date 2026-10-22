package com.nexora.restopos.service;

import com.nexora.restopos.dto.PublicSiteDtos;
import com.nexora.restopos.model.*;
import com.nexora.restopos.repository.CategoryRepository;
import com.nexora.restopos.repository.MesaRepository;
import com.nexora.restopos.repository.ProductRepository;
import com.nexora.restopos.repository.TiendaInfoRepository;
import com.nexora.restopos.repository.ZonaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Lectura de la página web pública de una tienda (menú, mesas, datos de contacto), resuelta
 * siempre por el slug de la URL — nunca por sesión. Si la tienda no tiene la página activada
 * (o el slug no existe) responde "no encontrada", sin distinguir un caso del otro.
 */
@Service
@RequiredArgsConstructor
public class PublicSiteService {

    private final TiendaInfoRepository tiendaInfoRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ZonaRepository zonaRepository;
    private final MesaRepository mesaRepository;

    /** Tienda con página web activa para ese slug, o error "no encontrada". */
    public TiendaInfo resolve(String slug) {
        return tiendaInfoRepository.findByPublicSlugIgnoreCase(slug == null ? "" : slug.trim())
                .filter(i -> Boolean.TRUE.equals(i.getWebEnabled()))
                .filter(i -> i.getTienda() != null && Boolean.TRUE.equals(i.getTienda().getIsActive()))
                .orElseThrow(() -> new IllegalArgumentException("Esta página no existe o no está disponible"));
    }

    public PublicSiteDtos.Tienda tienda(String slug) {
        TiendaInfo i = resolve(slug);
        Tienda t = i.getTienda();
        String direccion = Stream.of(i.getCalle(), i.getColonia(), i.getLocalidad(), i.getEstado())
                .filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.joining(", "));
        BigDecimal envio = i.getCostoEnvioDomicilio() != null && i.getCostoEnvioDomicilio().compareTo(BigDecimal.ZERO) > 0
                ? i.getCostoEnvioDomicilio() : BigDecimal.ZERO;
        boolean transferencia = Boolean.TRUE.equals(i.getWebPagoTransferencia());
        return new PublicSiteDtos.Tienda(
                t.getId(),
                t.getName(), t.getLogoPath(), t.getPrimaryColor(), i.getTelefono(),
                direccion.isEmpty() ? null : direccion, i.getWebMensaje(),
                Boolean.TRUE.equals(i.getWebMostrarMesas()),
                Boolean.TRUE.equals(i.getWebRecogerEnabled()),
                Boolean.TRUE.equals(i.getWebDomicilioEnabled()),
                envio,
                Boolean.TRUE.equals(i.getWebPagoEfectivo()),
                Boolean.TRUE.equals(i.getWebPagoTarjeta()),
                transferencia,
                transferencia ? i.getTransferenciaBanco() : null,
                transferencia ? i.getTransferenciaTitular() : null,
                transferencia ? i.getTransferenciaClabe() : null,
                Boolean.TRUE.equals(i.getWebReservasEnabled()));
    }

    /**
     * Menú pedible AHORA: categorías activas y dentro de horario (ver
     * {@link Category#isAvailableNow()}), con sus productos activos y disponibles. Las
     * categorías sin productos no se muestran; los productos sin categoría van en "Otros".
     */
    @Transactional(readOnly = true)
    public List<PublicSiteDtos.Categoria> menu(String slug) {
        Long tiendaId = resolve(slug).getTienda().getId();
        Map<Long, Category> categorias = categoryRepository.findAllForTienda(tiendaId).stream()
                .filter(Category::isAvailableNow)
                .collect(Collectors.toMap(Category::getId, c -> c, (a, b) -> a, LinkedHashMap::new));

        Map<Long, List<PublicSiteDtos.Producto>> porCategoria = new LinkedHashMap<>();
        List<PublicSiteDtos.Producto> sinCategoria = new ArrayList<>();
        for (Product p : productRepository.findAllActive(tiendaId)) {
            if (!Boolean.TRUE.equals(p.getDisponible())) continue;
            PublicSiteDtos.Producto dto = toDto(p);
            if (p.getCategory() == null) sinCategoria.add(dto);
            else if (categorias.containsKey(p.getCategory().getId())) {
                porCategoria.computeIfAbsent(p.getCategory().getId(), k -> new ArrayList<>()).add(dto);
            }
        }

        List<PublicSiteDtos.Categoria> out = new ArrayList<>();
        for (Category c : categorias.values()) {
            List<PublicSiteDtos.Producto> productos = porCategoria.get(c.getId());
            if (productos != null && !productos.isEmpty()) {
                out.add(new PublicSiteDtos.Categoria(c.getId(), c.getName(), c.getDescription(), productos));
            }
        }
        if (!sinCategoria.isEmpty()) out.add(new PublicSiteDtos.Categoria(null, "Otros", null, sinCategoria));
        return out;
    }

    private PublicSiteDtos.Producto toDto(Product p) {
        List<PublicSiteDtos.Grupo> grupos = p.getModificadorGrupos().stream()
                .filter(g -> Boolean.TRUE.equals(g.getIsActive()))
                .sorted(Comparator.comparing((ModificadorGrupo g) -> g.getOrden() == null ? 0 : g.getOrden())
                        .thenComparing(ModificadorGrupo::getId))
                .map(g -> new PublicSiteDtos.Grupo(g.getId(), g.getNombre(), g.getTipo().name(),
                        Boolean.TRUE.equals(g.getObligatorio()),
                        g.getOpciones().stream()
                                .filter(o -> Boolean.TRUE.equals(o.getIsActive()))
                                .sorted(Comparator.comparing((ModificadorOpcion o) -> o.getOrden() == null ? 0 : o.getOrden())
                                        .thenComparing(ModificadorOpcion::getId))
                                .map(o -> new PublicSiteDtos.Opcion(o.getId(), o.getNombre(), o.getPrecioExtra()))
                                .toList()))
                .filter(g -> !g.opciones().isEmpty())
                .toList();
        return new PublicSiteDtos.Producto(p.getId(), p.getName(), p.getDescription(), p.getPrice(), p.getImageUrl(), grupos,
                p.isSinInventario());
    }

    /**
     * Mesas por zona con su estado simplificado (LIBRE/OCUPADA/RESERVADA). "Cuenta pedida"
     * cuenta como ocupada y las fuera de servicio no se muestran. Solo si el admin lo permite.
     */
    @Transactional(readOnly = true)
    public List<PublicSiteDtos.Zona> mesas(String slug) {
        TiendaInfo info = resolve(slug);
        if (!Boolean.TRUE.equals(info.getWebMostrarMesas())) return List.of();
        Long tiendaId = info.getTienda().getId();

        Map<Long, List<Mesa>> porZona = mesaRepository.findAllForTienda(tiendaId).stream()
                .filter(m -> m.getZona() != null && m.getEstado() != MesaEstado.FUERA_DE_SERVICIO)
                .collect(Collectors.groupingBy(m -> m.getZona().getId()));

        List<PublicSiteDtos.Zona> out = new ArrayList<>();
        for (Zona z : zonaRepository.findAllForTienda(tiendaId)) {
            List<Mesa> mesas = porZona.getOrDefault(z.getId(), List.of());
            if (mesas.isEmpty()) continue;
            List<PublicSiteDtos.Mesa> dtos = mesas.stream()
                    .sorted(Comparator.comparing((Mesa m) -> numeroOrden(m.getNumero())).thenComparing(Mesa::getNumero))
                    .map(m -> new PublicSiteDtos.Mesa(m.getNumero(), m.getCapacidad(), estadoPublico(m.getEstado())))
                    .toList();
            int libres = (int) dtos.stream().filter(m -> "LIBRE".equals(m.estado())).count();
            out.add(new PublicSiteDtos.Zona(z.getNombre(), libres, dtos.size() - libres, dtos));
        }
        return out;
    }

    private static String estadoPublico(MesaEstado e) {
        return switch (e) {
            case DISPONIBLE -> "LIBRE";
            case RESERVADA -> "RESERVADA";
            default -> "OCUPADA";
        };
    }

    // Ordena "2" antes que "10" cuando el número de mesa es numérico.
    private static long numeroOrden(String numero) {
        try { return Long.parseLong(numero.trim()); } catch (Exception e) { return Long.MAX_VALUE; }
    }
}
