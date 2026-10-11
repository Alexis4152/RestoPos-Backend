package com.nexora.restopos.dto;

import lombok.Data;

/** Payload genérico para operaciones de cancelación que piden un motivo. */
@Data
public class CancelRequest {
    private String motivo;
}
