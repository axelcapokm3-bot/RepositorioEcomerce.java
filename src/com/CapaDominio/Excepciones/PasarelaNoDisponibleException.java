package com.CapaDominio.Excepciones;

public class PasarelaNoDisponibleException extends RuntimeException {
    public PasarelaNoDisponibleException(String mensaje) {
        super(mensaje);
    }

    public PasarelaNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
