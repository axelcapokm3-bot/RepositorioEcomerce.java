package com.CapaDominio.Entidades;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ItemCarritoTest {

    private ItemCarrito item(int cantidad) {
        Producto p = new Producto("Laptop", new BigDecimal("1000"), 10, 0, 1, CategoriaProducto.ELECTRONICA);
        return new ItemCarrito(p, cantidad);
    }

    @Test
    @DisplayName("Constructor: rechaza cantidad menor o igual a cero")
    void constructorRechazaCantidadInvalida() {
        Producto p = new Producto("Laptop", new BigDecimal("1000"), 10, 0, 1, CategoriaProducto.ELECTRONICA);
        assertThrows(IllegalArgumentException.class, () -> new ItemCarrito(p, 0));
        assertThrows(IllegalArgumentException.class, () -> new ItemCarrito(p, -1));
    }

    @Test
    @DisplayName("Subtotal bruto = precio * cantidad")
    void subtotalBruto() {
        assertEquals(new BigDecimal("2000"), item(2).getSubtotalBruto());
    }

    @Test
    @DisplayName("Subtotal sin descuento equivale al bruto")
    void subtotalSinDescuento() {
        assertEquals(new BigDecimal("2000"), item(2).getSubtotal(null));
    }

    @Test
    @DisplayName("Subtotal con descuento porcentual")
    void subtotalConDescuento() {
        IDescuentos descuento = new DescuentoPorPorcentaje(new BigDecimal("0.10"));
        assertBigDecimalIgual(new BigDecimal("1800"), item(2).getSubtotal(descuento));
    }

    @Test
    @DisplayName("Sumar cantidad acumula")
    void sumarCantidad() {
        ItemCarrito i = item(2);
        i.sumarCantidad(3);
        assertEquals(5, i.getCantidad());
    }

    private void assertBigDecimalIgual(BigDecimal esperado, BigDecimal actual) {
        assertEquals(0, esperado.compareTo(actual), () -> "Esperado " + esperado + " pero fue " + actual);
    }
}