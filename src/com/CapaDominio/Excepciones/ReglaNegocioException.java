package com.CapaDominio.Excepciones;

public class ReglaNegocioException extends BusinessException {
    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
