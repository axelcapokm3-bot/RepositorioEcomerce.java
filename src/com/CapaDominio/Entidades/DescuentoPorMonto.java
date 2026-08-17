package com.CapaDominio.Entidades;

import java.math.BigDecimal;

public class DescuentoPorMonto implements  IDescuentos {
    
private final BigDecimal montoDescuento;
private final BigDecimal montoMinimoRequerido;

    public DescuentoPorMonto(BigDecimal montoDescuento, BigDecimal montoMinimoRequerido) {
        this.montoDescuento = montoDescuento;
        this.montoMinimoRequerido = montoMinimoRequerido;
    }

    
    public String getNombre() {
        return "Descuento fijo de $" + montoDescuento;
    }

    
    public boolean esExclusivo() {
        return false;
    }

    
    public BigDecimal calcularDescuento(ItemCarrito item) {
     
        if (item.getSubtotalBruto().compareTo(montoMinimoRequerido) >= 0) {
            return montoDescuento;
        }
        return BigDecimal.ZERO;
}
}