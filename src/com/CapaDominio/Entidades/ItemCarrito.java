package com.CapaDominio.Entidades;

import java.math.BigDecimal;

public class ItemCarrito {
    private Producto producto; 
    private int cantidad;

public ItemCarrito(Producto producto, int cantidad) {
    if (cantidad <= 0) throw new IllegalArgumentException("La cantidad debe ser mayor a 0");
    this.producto = producto;
    this.cantidad = cantidad;
}

    public BigDecimal getSubtotalBruto() {
    return producto.getPrecio().multiply(BigDecimal.valueOf(cantidad));
}

    public BigDecimal getSubtotal(IDescuentos descuentos) {
       BigDecimal SubtotalBruto = getSubtotalBruto();
       if(descuentos == null) { 
        return SubtotalBruto  ;
       }

       BigDecimal montoDescuento = descuentos.calcularDescuento(this)  ;
       BigDecimal PrecioFinal = SubtotalBruto.subtract(montoDescuento); 

       return  (PrecioFinal.compareTo(BigDecimal.ZERO) < 0  )  ? BigDecimal.ZERO : PrecioFinal ; 

    }

    
    public Producto getProducto() { return producto; }
    public int getCantidad() { return cantidad; }
    public void sumarCantidad(int extra) { this.cantidad += extra; }
}
