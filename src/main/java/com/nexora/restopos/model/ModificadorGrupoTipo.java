package com.nexora.restopos.model;

/** Regla de selección de un {@link ModificadorGrupo}. */
public enum ModificadorGrupoTipo {
    /** A lo más una opción del grupo (radio) — ej. término de cocción. */
    UNICO,
    /** Cero o varias opciones del grupo (casillas) — ej. extras. */
    MULTIPLE
}
