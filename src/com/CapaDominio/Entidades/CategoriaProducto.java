package com.CapaDominio.Entidades;

import java.util.Locale;

public enum CategoriaProducto {
    ELECTRONICA,
    ROPA,
    HOGAR_Y_MUEBLES,
    DEPORTES,
    INFORMATICA,
    ALMACEN,
    HERRAMIENTAS,
    BEBIDAS,
    OTROS;

    public static CategoriaProducto desdeTexto(String texto) {
        String normalizado = normalizar(texto);
        if (normalizado.isEmpty()) {
            return OTROS;
        }
        for (CategoriaProducto categoria : values()) {
            if (categoria.name().equals(normalizado)) {
                return categoria;
            }
        }
        return OTROS;
    }

    public static boolean esConocida(String texto) {
        String normalizado = normalizar(texto);
        if (normalizado.isEmpty()) {
            return false;
        }
        for (CategoriaProducto categoria : values()) {
            if (categoria.name().equals(normalizado)) {
                return true;
            }
        }
        return false;
    }

    public static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.trim().toUpperCase(Locale.ROOT)
                .replace('Á', 'A')
                .replace('É', 'E')
                .replace('Í', 'I')
                .replace('Ó', 'O')
                .replace('Ú', 'U')
                .replace('Ü', 'U')
                .replace(' ', '_');
    }
}
