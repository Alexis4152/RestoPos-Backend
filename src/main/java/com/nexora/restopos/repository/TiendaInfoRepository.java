package com.nexora.restopos.repository;

import com.nexora.restopos.model.TiendaInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repositorio de {@link TiendaInfo}, los datos fiscales y de contacto de una tienda
 * (RFC, dirección, razón social, etc.), separados de la entidad Tienda para no cargar
 * estos campos en cada lugar que ya serializa la tienda (login, ventas, cortes...).
 * Se consultan/editan únicamente desde la pantalla de "Datos de la tienda" y se usan
 * al imprimir el ticket de venta en PDF.
 */
public interface TiendaInfoRepository extends JpaRepository<TiendaInfo, Long> {
    /** Obtiene los datos fiscales/de contacto de una tienda, si ya fueron capturados. */
    Optional<TiendaInfo> findByTiendaId(Long tiendaId);

    /** Resuelve la tienda de la página web pública por su slug (ver PublicController). */
    Optional<TiendaInfo> findByPublicSlugIgnoreCase(String publicSlug);

    /** Si el slug ya lo usa OTRA tienda (validación de unicidad al configurarlo). */
    boolean existsByPublicSlugIgnoreCaseAndTiendaIdNot(String publicSlug, Long tiendaId);
}
