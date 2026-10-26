package com.CapaDominio.Excepciones;

public class NegocioInactivoException extends BusinessException {
    public NegocioInactivoException(String mensaje) {
        super(mensaje);
    }

    public NegocioInactivoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
