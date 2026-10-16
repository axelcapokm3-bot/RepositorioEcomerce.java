package com.CapaDominio.Entidades;

public enum EstadoCuenta {
    ACTIVA,
    BLOQUEADA,
    BAJA;

    public static EstadoCuenta desdeTexto(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            throw new IllegalArgumentException("El estado de la cuenta es obligatorio.");
        }
        String normalizado = texto.trim().toUpperCase();
        for (EstadoCuenta estado : EstadoCuenta.values()) {
            if (estado.name().equals(normalizado)) {
                return estado;
            }
        }
        throw new IllegalArgumentException("El estado de la cuenta no es válido. Valores permitidos: ACTIVA, BLOQUEADA, BAJA.");
    }
}
