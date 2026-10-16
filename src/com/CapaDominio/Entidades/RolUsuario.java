package com.CapaDominio.Entidades;

public enum RolUsuario {
    ADMIN,
    CLIENTE;

    public static RolUsuario desdeTexto(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            throw new IllegalArgumentException("El rol de usuario es obligatorio.");
        }
        String normalizado = texto.trim().toUpperCase();
        for (RolUsuario rol : RolUsuario.values()) {
            if (rol.name().equals(normalizado)) {
                return rol;
            }
        }
        throw new IllegalArgumentException("El rol de usuario no es válido. Valores permitidos: ADMIN, CLIENTE.");
    }
}
