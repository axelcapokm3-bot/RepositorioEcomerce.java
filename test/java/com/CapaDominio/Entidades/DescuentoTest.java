package com.CapaDominio.Entidades;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DescuentoTest {

    private ItemCarrito itemConSubtotal(int cantidad, BigDecimal precio) {
        Producto p = new Producto("P", precio, 100, 0, 1, CategoriaProducto.ROPA);
        return new ItemCarrito(p, cantidad);
    }

    @Test
    @DisplayName("Descuento por porcentaje: aplica porcentaje al subtotal")
    void descuentoPorcentaje() {
        IDescuentos d = new DescuentoPorPorcentaje(new BigDecimal("0.10"));
        ItemCarrito item = itemConSubtotal(3, new BigDecimal("100"));
        assertBigDecimalIgual(new BigDecimal("30"), d.calcularDescuento(item));
        assertBigDecimalIgual(new BigDecimal("270"), item.getSubtotal(d));
    }

    @Test
    @DisplayName("Descuento por monto: solo aplica si supera el mínimo")
    void descuentoPorMonto() {
        IDescuentos d = new DescuentoPorMonto(new BigDecimal("20"), new BigDecimal("100"));
        ItemCarrito bajo = itemConSubtotal(1, new BigDecimal("50"));
        assertBigDecimalIgual(BigDecimal.ZERO, d.calcularDescuento(bajo));

        ItemCarrito alto = itemConSubtotal(3, new BigDecimal("50"));
        assertBigDecimalIgual(new BigDecimal("20"), d.calcularDescuento(alto));
        assertBigDecimalIgual(new BigDecimal("130"), alto.getSubtotal(d));
    }

    @Test
    @DisplayName("Descuento especial (Black Friday): 50% y exclusivo")
    void descuentoEspecial() {
        IDescuentos d = new DescuentosEspeciales();
        assertTrue(d.esExclusivo());
        ItemCarrito item = itemConSubtotal(2, new BigDecimal("100"));
        assertBigDecimalIgual(new BigDecimal("100"), d.calcularDescuento(item));
        assertBigDecimalIgual(new BigDecimal("100"), item.getSubtotal(d));
    }

    @Test
    @DisplayName("El subtotal nunca es negativo aunque el descuento supere el total")
    void subtotalNuncaNegativo() {
        IDescuentos d = new DescuentoPorMonto(new BigDecimal("500"), new BigDecimal("0"));
        ItemCarrito item = itemConSubtotal(1, new BigDecimal("100"));
        assertBigDecimalIgual(BigDecimal.ZERO, item.getSubtotal(d));
    }

    private void assertBigDecimalIgual(BigDecimal esperado, BigDecimal actual) {
        assertEquals(0, esperado.compareTo(actual), () -> "Esperado " + esperado + " pero fue " + actual);
    }
}