package com.CapaDominio.Entidades;

import java.math.BigDecimal;

public interface IDescuentos {
    public    String getNombre();
    public   boolean esExclusivo();
    public   BigDecimal calcularDescuento(ItemCarrito item); 
}