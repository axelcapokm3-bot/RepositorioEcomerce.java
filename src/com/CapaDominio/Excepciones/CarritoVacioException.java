package com.CapaDominio.Excepciones;

public class CarritoVacioException extends BusinessException {
    public CarritoVacioException(String mensaje) {
        super(mensaje);
    }
}
