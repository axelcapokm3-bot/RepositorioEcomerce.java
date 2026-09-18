package com.CapaDominio.Excepciones;

public class StockInsuficienteException extends BusinessException {
    public StockInsuficienteException(String mensaje) {
        super(mensaje);
    }
}
