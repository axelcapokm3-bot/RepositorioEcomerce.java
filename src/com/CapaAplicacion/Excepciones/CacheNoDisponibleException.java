package com.CapaAplicacion.Excepciones;

public class CacheNoDisponibleException extends RuntimeException {

    public CacheNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
