package com.CapaDominio.Excepciones;

public class PagoRechazadoException extends RuntimeException {
    public PagoRechazadoException(String mensaje) {
        super(mensaje);
    }

    public PagoRechazadoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
