package com.nexora.restopos.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Payload de {@code PUT /api/cash-registers/report-recipients}: a quién le llega el reporte
 * de fin de día de la tienda. El supervisor lo recibe por default ({@code reportToSupervisor});
 * se le pueden sumar usuarios de la tienda y correos libres.
 */
@Data
public class ReportRecipientsRequest {
    private Boolean reportToSupervisor;

    @Size(max = 20, message = "Máximo 20 usuarios")
    private List<Long> userIds = new ArrayList<>();

    @Size(max = 10, message = "Máximo 10 correos adicionales")
    private List<@Email(message = "Hay un correo con formato inválido")
                 @Size(max = 150, message = "Un correo no puede tener más de 150 caracteres") String> emails = new ArrayList<>();
}
