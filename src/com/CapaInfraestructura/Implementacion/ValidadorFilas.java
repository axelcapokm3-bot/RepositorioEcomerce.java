package com.CapaInfraestructura.Implementacion;

import com.CapaDominio.Excepciones.RecursoNoEncontradoException;

final class ValidadorFilas {

    private ValidadorFilas() {
    }

    static void exigirUnaFila(int filas, String recurso, Object id) {
        if (filas != 1) {
            throw new RecursoNoEncontradoException(mensaje(recurso, id));
        }
    }

    static void exigirFila(int filas, String recurso, Object id) {
        if (filas < 1) {
            throw new RecursoNoEncontradoException(mensaje(recurso, id));
        }
    }

    private static String mensaje(String recurso, Object id) {
        return "No se encontró " + recurso + " con id " + id + ".";
    }
}
