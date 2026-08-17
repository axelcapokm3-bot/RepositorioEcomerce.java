package com.CapaDominio.Entidades;

import java.math.BigDecimal;

public class DescuentoPorPorcentaje implements IDescuentos {
    private final BigDecimal porcentaje ; 

    public DescuentoPorPorcentaje(BigDecimal porcentaje) {
        this.porcentaje = porcentaje;
    }


    public String getNombre() {
        return "Descuento del " + (porcentaje.multiply(porcentaje)) + "%";
    }

    public boolean esExclusivo() {
        return false; 


    }

    public BigDecimal calcularDescuento(ItemCarrito item) {
        return item.getSubtotalBruto().multiply(porcentaje);
    }
}






