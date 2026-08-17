package com.CapaDominio.Entidades;

import java.math.BigDecimal;

public class DescuentosEspeciales  implements  IDescuentos{
@Override
    public String getNombre() {
        return "BLACK FRIDAY 50% OFF";
    }

    @Override
    public boolean esExclusivo() {
        return true;
    }

    @Override
    public BigDecimal calcularDescuento(ItemCarrito item) {
        return item.getSubtotalBruto().multiply(BigDecimal.valueOf(0.50));
    }
}