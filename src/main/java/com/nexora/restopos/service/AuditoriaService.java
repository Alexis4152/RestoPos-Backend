package com.nexora.restopos.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexora.restopos.model.Auditoria;
import com.nexora.restopos.model.User;
import com.nexora.restopos.repository.AuditoriaRepository;
import com.nexora.restopos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Bitácora de operaciones sensibles (cancelaciones, caja, pagos, etc.), consultable solo por
 * ADMIN/SUPER_ADMIN. {@code before}/{@code after} se serializan a JSON con Jackson; un fallo de
 * serialización nunca debe tronar la operación de negocio que está siendo auditada, así que
 * {@link #log} atrapa cualquier excepción y solo la registra en el log de la aplicación.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditoriaService {

    private final AuditoriaRepository auditoriaRepository;
    private final TenantScope tenantScope;
    private final ObjectMapper objectMapper;

    public void log(User actor, String accion, String entidad, Long entidadId, Object before, Object after, String motivo) {
        try {
            Auditoria a = new Auditoria();
            a.setUsuario(actor);
            a.setTienda(actor != null ? actor.getTienda() : null);
            a.setAccion(accion);
            a.setEntidad(entidad);
            a.setEntidadId(entidadId);
            a.setValorAnterior(toJson(before));
            a.setValorNuevo(toJson(after));
            a.setMotivo(motivo);
            auditoriaRepository.save(a);
        } catch (RuntimeException e) {
            log.error("No se pudo registrar auditoría ({} {}#{}): {}", accion, entidad, entidadId, e.getMessage());
        }
    }

    public Page<Auditoria> findAll(Pageable pageable, User actor) {
        return auditoriaRepository.search(tenantScope.scopeId(actor), pageable);
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }
}
