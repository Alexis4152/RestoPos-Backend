package com.nexora.restopos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nexora.restopos.security.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Configuración SMTP única y global con la que el backend manda TODOS los correos del
 * sistema (tickets, reportes de cierre de caja, contraseñas temporales, recuperación de
 * contraseña, avisos a clientes de la página web y reservaciones). Antes vivía fija en
 * application.properties/variables de entorno; ahora es una fila editable en caliente desde
 * {@code /api/admin/mail-config}, sin redeploy para rotar la contraseña o cambiar de cuenta.
 * <p>
 * Solo debe existir una fila (la siembra {@code MailConfigDataInitializer} al primer
 * arranque). Solo un SUPER_ADMIN puede leerla/editarla — es infraestructura de la
 * plataforma, no algo por tienda.
 */
@Entity
@Table(name = "mail_config")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class MailConfig {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "smtp_host", nullable = false, length = 150)
    private String smtpHost;

    @Column(name = "smtp_port", nullable = false)
    private Integer smtpPort;

    @Column(name = "smtp_username", length = 150)
    private String smtpUsername;

    // Cifrada con AES-256-GCM en la columna (ver EncryptedStringConverter).
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "smtp_password", length = 255)
    private String smtpPassword;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_user_id")
    private User updatedBy;
}
