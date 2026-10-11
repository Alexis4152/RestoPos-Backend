package com.nexora.restopos.model;

/** Estado operativo de una {@link Mesa}. */
public enum MesaEstado {
    DISPONIBLE,
    OCUPADA,
    RESERVADA,
    PENDIENTE_PAGO,
    FUERA_DE_SERVICIO
}
