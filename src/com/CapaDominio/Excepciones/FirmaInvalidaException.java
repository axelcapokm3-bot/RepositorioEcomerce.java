package com.CapaDominio.Excepciones;

public class FirmaInvalidaException extends RuntimeException {
    public FirmaInvalidaException(String mensaje) {
        super(mensaje);
    }

    public FirmaInvalidaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
