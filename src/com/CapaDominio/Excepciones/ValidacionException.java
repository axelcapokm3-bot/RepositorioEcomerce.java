package com.CapaDominio.Excepciones;

public class ValidacionException extends BusinessException {
    public ValidacionException(String mensaje) {
        super(mensaje);
    }

    public ValidacionException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
