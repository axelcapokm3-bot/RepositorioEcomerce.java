package com.CapaDominio.Excepciones;

public class IdentidadDuplicadaException extends ReglaNegocioException {
    public IdentidadDuplicadaException(String mensaje) {
        super(mensaje);
    }

    public IdentidadDuplicadaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
