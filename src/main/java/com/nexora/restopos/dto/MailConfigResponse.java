package com.nexora.restopos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Nunca incluye la contraseña real: solo indica si ya hay una guardada ({@code passwordConfigured}). */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MailConfigResponse {
    private boolean enabled;
    private String smtpHost;
    private Integer smtpPort;
    private String smtpUsername;
    private boolean passwordConfigured;
}
