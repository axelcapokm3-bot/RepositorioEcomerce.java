package com.CapaDominio.Excepciones;

/** Indica que un pago intentó pasar a un estado no permitido. */
public class TransicionPagoInvalidaException extends BusinessException {
    public TransicionPagoInvalidaException(String mensaje) {
        super(mensaje);
    }
}
