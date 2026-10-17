package com.nexora.restopos.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Destinatario adicional del reporte de fin de día de una tienda: un usuario del sistema
 * (admin, cajero...) o un correo libre (alguien sin acceso al sistema). El supervisor de la
 * tienda lo recibe por default sin configurar nada (ver {@code Tienda.reportToSupervisor});
 * estos se suman a él.
 */
@Entity
@Table(name = "cash_report_recipients")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CashReportRecipient {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tienda_id", nullable = false)
    private Tienda tienda;

    /** Usuario del sistema; null si es un correo libre. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /** Correo libre; null si es un usuario del sistema. */
    @Column(length = 150)
    private String email;
}
