package com.nexora.restopos.exception;

/** El usuario pertenece a una tienda desactivada: no puede entrar ni seguir usando el sistema. */
public class TiendaInactivaException extends RuntimeException {

    public static final String MENSAJE = "Sin acceso, comuníquese con el administrador";
    /** Marca que viaja en {@code data} de la respuesta para que el frontend cierre la sesión. */
    public static final String CODIGO = "TIENDA_INACTIVA";

    public TiendaInactivaException() {
        super(MENSAJE);
    }
}
