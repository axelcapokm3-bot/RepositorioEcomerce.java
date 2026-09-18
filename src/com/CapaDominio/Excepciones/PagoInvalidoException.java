package com.CapaDominio.Excepciones;

public class PagoInvalidoException extends BusinessException {
    public PagoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
