package com.nexora.restopos.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

/** 👍 / 👎 de un platillo dentro de una {@link Opinion}. El nombre se copia (el producto puede renombrarse). */
@Entity
@Table(name = "opinion_platillos")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class OpinionPlatillo {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "opinion_id", nullable = false)
    private Opinion opinion;

    @Column(nullable = false)
    private Long ordenDetalleId;

    @Column(nullable = false, length = 200)
    private String productoNombre;

    @Column(nullable = false)
    private Boolean meGusta;
}
