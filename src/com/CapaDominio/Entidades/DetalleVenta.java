package com.CapaDominio.Entidades;

import java.math.BigDecimal;

public class DetalleVenta {
    private int idProducto;
    private BigDecimal precioUnitario; 
    private int cantidad;

    public DetalleVenta(int idProducto, BigDecimal precioUnitario, int cantidad) {
        if (idProducto <= 0) {
            throw new IllegalArgumentException("El ID del producto debe ser válido.");
        }
        if (precioUnitario == null || precioUnitario.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El precio unitario no es válido.");
        }
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor a cero.");
        }

        this.idProducto = idProducto;
        this.precioUnitario = precioUnitario;
        this.cantidad = cantidad;
    }


    public BigDecimal getSubtotal() {
        return this.precioUnitario.multiply(BigDecimal.valueOf(this.cantidad));
    }

    public int getIdProducto() {
        return idProducto;
    }

    public BigDecimal getPrecioUnitario() {
        return precioUnitario;
    }

    public int getCantidad() {
        return cantidad;
    }
}