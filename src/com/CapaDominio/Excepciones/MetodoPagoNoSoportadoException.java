package com.CapaDominio.Excepciones;

public class MetodoPagoNoSoportadoException extends RuntimeException {
    public MetodoPagoNoSoportadoException(String mensaje) {
        super(mensaje);
    }
}
