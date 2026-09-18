package com.CapaDominio.Excepciones;

public abstract class BusinessException extends RuntimeException {
    public BusinessException(String mensaje) {
        super(mensaje);
    }

    public BusinessException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
